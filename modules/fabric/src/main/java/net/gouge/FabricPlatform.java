package net.gouge;

import net.fabricmc.loader.api.FabricLoader;
import net.gouge.platform.GougePlatform;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;

public final class FabricPlatform implements GougePlatform {

    private final boolean paraglider = FabricLoader.getInstance().isModLoaded("paraglider");

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasModStamina() {
        return paraglider;
    }

    @Override
    public boolean staminaExhausted(Player player) {
        return paraglider && net.gouge.extern.paraglider.ParagliderStamina.exhausted(player);
    }
}
