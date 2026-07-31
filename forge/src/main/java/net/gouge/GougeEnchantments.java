package net.gouge;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GougeEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Gouge.MOD_ID);

    public static final RegistryObject<Enchantment> GRIP = ENCHANTMENTS.register("grip", GougeEnchantment::new);
    public static final RegistryObject<Enchantment> MOMENTUM = ENCHANTMENTS.register("momentum", GougeEnchantment::new);

    private GougeEnchantments() {}
}
