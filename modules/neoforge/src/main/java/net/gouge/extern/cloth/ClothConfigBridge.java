package net.gouge.extern.cloth;

import net.gouge.GougeConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class ClothConfigBridge {

    private ClothConfigBridge() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> GougeConfigScreen.create(parent));
    }
}
