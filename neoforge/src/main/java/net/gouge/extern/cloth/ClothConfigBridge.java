package net.gouge.extern.cloth;

import net.gouge.GougeConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class ClothConfigBridge {

    private ClothConfigBridge() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> GougeConfigScreen.create(parent)));
    }
}
