package net.gouge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ToolActions;

import java.nio.file.Path;

public final class NeoForgePlatform implements GougePlatform {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return stack.getItem().canPerformAction(stack, ToolActions.PICKAXE_DIG);
    }
}
