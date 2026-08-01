package net.gouge;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class GougeEnchantments {
    public static final Enchantment GRIP = register("grip");
    public static final Enchantment MOMENTUM = register("momentum");

    private static Enchantment register(String name) {
        return Registry.register(Registries.ENCHANTMENT, Identifier.of("gouge", name), new GougeEnchantment());
    }

    public static void init() {}

    private GougeEnchantments() {}
}
