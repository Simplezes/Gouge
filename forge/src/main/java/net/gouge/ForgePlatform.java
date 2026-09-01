package net.gouge;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgePlatform implements GougePlatform {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPickaxeTool(ItemStack stack) {
        return stack.getItem().canPerformAction(stack, ToolActions.PICKAXE_DIG);
    }
}
