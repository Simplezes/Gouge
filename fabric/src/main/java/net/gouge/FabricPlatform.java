package net.gouge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;

public final class FabricPlatform implements GougePlatform {

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return false;
    }
}
