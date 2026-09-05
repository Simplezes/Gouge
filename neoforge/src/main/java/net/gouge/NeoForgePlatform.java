package net.gouge;

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
    public boolean blocksGouge(UUID playerId) {
        return parcool && net.gouge.extern.parcool.ParCoolCompat.isWallActionActive(playerId);
    }
}
