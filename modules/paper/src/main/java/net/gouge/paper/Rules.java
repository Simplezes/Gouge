package net.gouge.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.gouge.paper.Tuning.*;

final class Rules {

    private final GougePlugin plugin;
    private final Map<Material, double[]> pickaxes = new EnumMap<>(Material.class);
    private final Map<String, Boolean> overrides = new HashMap<>();
    double[] fallback;

    Rules(GougePlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        pickaxes.clear();
        overrides.clear();
        var sec = plugin.getConfig().getConfigurationSection("pickaxes");
        fallback = pair(sec.getDoubleList("fallback"));
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) continue;
            double[] st = pair(sec.getDoubleList(m.getKey().toString()));
            if (st == null) {
                for (String key : sec.getKeys(false)) {
                    if (key.startsWith("#") && tagHas(key.substring(1), m)) {
                        st = pair(sec.getDoubleList(key));
                        break;
                    }
                }
            }
            if (st != null || Tag.ITEMS_PICKAXES.isTagged(m)) pickaxes.put(m, st != null ? st : fallback);
        }
        var ov = plugin.getConfig().getConfigurationSection("block_overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                String v = ov.getString(key, "");
                if (v.equals("hard") || v.equals("soft")) overrides.put(key, v.equals("hard"));
                else plugin.getLogger().warning("Ignoring block_overrides." + key + ": must be \"hard\" or \"soft\"");
            }
        }
    }

    private static double[] pair(List<Double> l) {
        return l.size() < 2 ? null : new double[]{l.get(0), l.get(1)};
    }

    private static boolean tagHas(String key, Material m) {
        NamespacedKey k = NamespacedKey.fromString(key);
        Tag<Material> t = k == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, k, Material.class);
        return t != null && t.isTagged(m);
    }

    int springCapBonus(ItemStack tool) {
        return enchLevel(tool, "spring") * plugin.getConfig().getInt("enchantments.spring_cap_bonus");
    }

    double spring(ItemStack tool) {
        return 1.0 + enchLevel(tool, "spring") * plugin.getConfig().getDouble("enchantments.spring_bonus");
    }

    double gripMult(Block b) {
        var sec = plugin.getConfig().getConfigurationSection("block_grip");
        if (sec == null) return 1.0;
        String id = b.getType().getKey().toString();
        if (sec.isSet(id)) return sec.getDouble(id);
        for (String key : sec.getKeys(false)) {
            if (!key.startsWith("#")) continue;
            NamespacedKey k = NamespacedKey.fromString(key.substring(1));
            Tag<Material> t = k == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, k, Material.class);
            if (t != null && t.isTagged(b.getType())) return sec.getDouble(key);
        }
        return 1.0;
    }

    double hangHold() {
        var c = plugin.getConfig();
        return Math.max(HANG_LOCK * 2, c.getBoolean("climbing.enabled") ? c.getDouble("climbing.speed") * 1.5 : 0);
    }

    boolean isPickaxe(ItemStack it) {
        return it != null && pickaxes.containsKey(it.getType());
    }

    boolean dualActive(Player p) {
        var inv = p.getInventory();
        return plugin.getConfig().getBoolean("dual_grip_enabled")
                && isPickaxe(inv.getItemInMainHand()) && isPickaxe(inv.getItemInOffHand());
    }

    ItemStack heldPickaxe(Player p) {
        var inv = p.getInventory();
        if (isPickaxe(inv.getItemInMainHand())) return inv.getItemInMainHand();
        return isPickaxe(inv.getItemInOffHand()) ? inv.getItemInOffHand() : null;
    }

    EquipmentSlot slotOf(Player p) {
        return isPickaxe(p.getInventory().getItemInMainHand()) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
    }

    boolean damage(Player p, EquipmentSlot slot, int amount) {
        ItemStack it = p.getInventory().getItem(slot);
        if (!(it.getItemMeta() instanceof Damageable d) || it.getType().getMaxDurability() <= 0) return true;
        int unb = enchLevel(it, "minecraft", "unbreaking");
        int dmg = 0;
        for (int i = 0; i < amount; i++) {
            if (unb == 0 || Math.random() < 1.0 / (unb + 1)) dmg++;
        }
        if (dmg == 0) return true;
        d.setDamage(d.getDamage() + dmg);
        if (d.getDamage() >= it.getType().getMaxDurability()) {
            p.getInventory().setItem(slot, null);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
            return false;
        }
        it.setItemMeta(d);
        return true;
    }

    boolean drain(Player p, EquipmentSlot slot, ItemStack tool) {
        int rate = (int) stats(tool)[1];
        return rate <= 0 || damage(p, slot, rate);
    }

    double[] stats(ItemStack tool) {
        return pickaxes.getOrDefault(tool.getType(), fallback);
    }

    int enchLevel(ItemStack it, String name) {
        return enchLevel(it, "gouge", name);
    }

    static int enchLevel(ItemStack it, String ns, String name) {
        Enchantment e = Registry.ENCHANTMENT.get(new NamespacedKey(ns, name));
        return e == null ? 0 : it.getEnchantmentLevel(e);
    }

    int hangTicks(ItemStack tool) {
        int base = (int) (stats(tool)[0] * 20);
        int bonus = (int) (plugin.getConfig().getDouble("enchantments.grip_bonus") * 20);
        return base + enchLevel(tool, "grip") * bonus;
    }

    boolean isHard(Block b) {
        Boolean o = overrides.isEmpty() ? null : overrides.get(b.getType().getKey().toString());
        if (o != null) return o;
        return plugin.getConfig().getBoolean("cling_everything") || b.getBlockData().requiresCorrectToolForDrops();
    }
}
