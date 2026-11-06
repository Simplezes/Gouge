package net.gouge.extern.cloth;

import net.gouge.GougeConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.ConfigScreenHandler;

public final class ClothConfigBridge {

    private ClothConfigBridge() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> GougeConfigScreen.create(parent)));
    }
}
