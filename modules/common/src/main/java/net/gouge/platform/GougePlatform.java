package net.gouge.platform;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.UUID;

public interface GougePlatform {

    Path configDir();

    boolean isPickaxeTool(ItemStack stack);

    default boolean blocksGouge(UUID playerId) {
        return false;
    }

    default boolean hasModStamina() {
        return false;
    }

    default boolean staminaExhausted(Player player) {
        return false;
    }

    default void spendStamina(Player player, double fractionOfMax) {
    }

    static void set(GougePlatform platform) {
        Holder.instance = platform;
    }

    static GougePlatform get() {
        return Holder.instance;
    }

    final class Holder {

        private static GougePlatform instance;

        private Holder() {
        }
    }
}
