package net.gouge;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GougeEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Gouge.MOD_ID);

    public static final RegistryObject<Enchantment> GRIP =
            ENCHANTMENTS.register("grip", () -> new GougeEnchantment(3));
    public static final RegistryObject<Enchantment> MOMENTUM =
            ENCHANTMENTS.register("momentum", () -> new GougeEnchantment(3));

    private GougeEnchantments() {}

    private static final class GougeEnchantment extends Enchantment {
        private final int maxLevel;

        GougeEnchantment(int maxLevel) {
            super(Rarity.UNCOMMON, EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
            this.maxLevel = maxLevel;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public boolean canEnchant(ItemStack stack) {
            return stack.getItem() instanceof PickaxeItem;
        }
    }
}
