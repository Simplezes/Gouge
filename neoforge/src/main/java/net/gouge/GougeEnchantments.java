package net.gouge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GougeEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, Gouge.MOD_ID);

    public static final DeferredHolder<Enchantment, Enchantment> GRIP = ENCHANTMENTS.register("grip", GougeEnchantment::new);
    public static final DeferredHolder<Enchantment, Enchantment> MOMENTUM = ENCHANTMENTS.register("momentum", GougeEnchantment::new);

    private GougeEnchantments() {}
}
