package net.gouge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class Gouge implements ModInitializer {
    public static final String MOD_ID = "gouge";

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GougePhysics.cleanup(handler.player.getUuid()));
    }
}
