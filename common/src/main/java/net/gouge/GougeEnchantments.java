package net.gouge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class GougeEnchantments {
    public static final ResourceKey<Enchantment> GRIP = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("gouge", "grip"));
    public static final ResourceKey<Enchantment> MOMENTUM = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("gouge", "momentum"));

    private GougeEnchantments() {}
}
