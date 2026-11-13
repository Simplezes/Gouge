package net.gouge.enchant;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

public final class GougeEnchantments {
    public static final ResourceKey<Enchantment> GRIP = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("gouge", "grip"));
    public static final ResourceKey<Enchantment> MOMENTUM = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("gouge", "momentum"));
    public static final ResourceKey<Enchantment> SPRING = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("gouge", "spring"));
    public static final ResourceKey<Enchantment> SHIMMY = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("gouge", "shimmy"));

    private GougeEnchantments() {}
}
