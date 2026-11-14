package net.gouge.physics;

import net.gouge.config.ConfigData;
import net.gouge.config.GougeConfig;
import net.gouge.enchant.GougeEnchantments;
import net.gouge.platform.GougePlatform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Pickaxes {

    private Pickaxes() {
    }

    static final ConfigData.PickaxeStats FALLBACK_STATS = new ConfigData.PickaxeStats(3.0, 2);
    static volatile List<Map.Entry<TagKey<Item>, ConfigData.PickaxeStats>> tagStatsCache;

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || GougePlatform.get().isPickaxeTool(stack);
    }

    public static boolean hasDualPickaxes(Player player) {
        return isPickaxe(player.getMainHandItem()) && isPickaxe(player.getOffhandItem());
    }

    public static boolean isDualGripActive(Player player) {
        return GougeConfig.INSTANCE.mechanics.dual_grip_enabled && hasDualPickaxes(player);
    }

    public static int springLevel(ItemStack stack) {
        return enchLevel(stack, GougeEnchantments.SPRING);
    }

    public static int springCapBonus(ItemStack stack) {
        return springLevel(stack) * GougeConfig.INSTANCE.enchantments.spring_cap_bonus;
    }

    static double springScale(ItemStack stack) {
        return 1.0 + springLevel(stack) * GougeConfig.INSTANCE.enchantments.spring_bonus;
    }

    public static void invalidateCache() {
        tagStatsCache = null;
    }

    static int enchLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var holder : enchants.keySet()) {
            if (holder.is(key)) {
                return enchants.getLevel(holder);
            }
        }
        return 0;
    }

    static List<Map.Entry<TagKey<Item>, ConfigData.PickaxeStats>> tagStats() {
        List<Map.Entry<TagKey<Item>, ConfigData.PickaxeStats>> cache = tagStatsCache;
        if (cache != null) {
            return cache;
        }
        List<Map.Entry<TagKey<Item>, ConfigData.PickaxeStats>> built = new ArrayList<>();
        for (var entry : GougeConfig.INSTANCE.pickaxes.entrySet()) {
            if (!entry.getKey().startsWith("#")) {
                continue;
            }
            Identifier tagId = Identifier.tryParse(entry.getKey().substring(1));
            if (tagId == null) {
                GougeConfig.LOGGER.warn("Ignoring invalid pickaxe tag key in gouge.toml: {}", entry.getKey());
                continue;
            }
            built.add(Map.entry(TagKey.create(Registries.ITEM, tagId), entry.getValue()));
        }
        tagStatsCache = built;
        return built;
    }

    static ConfigData.PickaxeStats getStats(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ConfigData.PickaxeStats direct = GougeConfig.INSTANCE.pickaxes.get(id.toString());
        if (direct != null) {
            return direct;
        }
        for (var entry : tagStats()) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        ConfigData.PickaxeStats fallback = GougeConfig.INSTANCE.pickaxes.get("fallback");
        return fallback != null ? fallback : FALLBACK_STATS;
    }

    public static int singlePickaxeHangTicks(ItemStack stack) {
        ConfigData.PickaxeStats stats = getStats(stack);
        int baseTicks = (int) (stats.hang_time * 20);
        int bonusTicks = (int) (GougeConfig.INSTANCE.enchantments.grip_bonus * 20);
        return baseTicks + enchLevel(stack, GougeEnchantments.GRIP) * bonusTicks;
    }

    public static int maxHangTicks(Player player, ItemStack stack) {
        int ticks = singlePickaxeHangTicks(stack);
        if (isDualGripActive(player)) {
            ticks = (int) Math.round(ticks * GougeConfig.INSTANCE.mechanics.dual_grip_multiplier);
        }
        return Math.max(1, ticks);
    }

    public static boolean applyImpactDamage(ItemStack stack, ServerLevel world, ServerPlayer player,
            double downwardSpeed, float hardness, EquipmentSlot slot) {
        int damage = Math.round((float) (downwardSpeed * Math.min(hardness, 5.0f) * 3));
        if (damage > 0) {
            stack.hurtAndBreak(damage, player, slot);
        }
        return !stack.isEmpty();
    }
}
