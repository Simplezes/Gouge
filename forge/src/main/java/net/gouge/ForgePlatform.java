package net.gouge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.UUID;

public final class ForgePlatform implements GougePlatform {

    private final boolean parcool = ModList.get().isLoaded("parcool");

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return stack.getItem().canPerformAction(stack, ToolActions.PICKAXE_DIG);
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
