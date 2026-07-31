package net.gouge;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

// 1.20.1 predates the data-component/datapack-enchantment system introduced in 1.20.5,
// so Grip and Momentum are plain registered Enchantment subclasses instead of data/gouge/enchantment/*.json.
public final class GougeEnchantments {
    public static final Enchantment GRIP = register("grip", 3);
    public static final Enchantment MOMENTUM = register("momentum", 3);

    private GougeEnchantments() {}

    /** No-op call used to force this class (and its registrations) to load. */
    public static void init() {}

    private static Enchantment register(String id, int maxLevel) {
        return Registry.register(Registries.ENCHANTMENT, new Identifier("gouge", id),
                new GougeEnchantment(maxLevel));
    }

    private static final class GougeEnchantment extends Enchantment {
        private final int maxLevel;

        GougeEnchantment(int maxLevel) {
            super(Rarity.UNCOMMON, EnchantmentTarget.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
            this.maxLevel = maxLevel;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public boolean isAcceptableItem(ItemStack stack) {
            return stack.getItem() instanceof PickaxeItem;
        }
    }
}
