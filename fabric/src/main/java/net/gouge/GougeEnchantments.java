package net.gouge;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class GougeEnchantments {
    public static final RegistryKey<Enchantment> GRIP =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("gouge", "grip"));
public static final RegistryKey<Enchantment> MOMENTUM =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("gouge", "momentum"));

    private GougeEnchantments() {}
}
