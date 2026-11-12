package net.gouge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.UUID;

public final class NeoForgePlatform implements GougePlatform {

    private final boolean parcool = ModList.get().isLoaded("parcool");

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasModStamina() {
        return parcool && net.gouge.extern.parcool.ParCoolStamina.available();
    }

    @Override
    public boolean staminaExhausted(Player player) {
        return parcool && net.gouge.extern.parcool.ParCoolStamina.exhausted(player);
    }

    @Override
    public void spendStamina(Player player, double fractionOfMax) {
        if (parcool) {
            net.gouge.extern.parcool.ParCoolStamina.spend(player, fractionOfMax);
        }
    }

    @Override
    public boolean blocksGouge(UUID playerId) {
        return parcool && net.gouge.extern.parcool.ParCoolCompat.isWallActionActive(playerId);
    }
}
