package net.gouge;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class Gouge implements ModInitializer {
    public static final String MOD_ID = "gouge";

    @Override
    public void onInitialize() {
        GougeConfig.load();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GougePhysics.cleanup(handler.player.getUuid()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("gouge")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("reload")
                            .executes(context -> {
                                GougeConfig.load();
                                context.getSource().sendFeedback(() -> Text.literal("Gouge config reloaded!"), true);
                                return Command.SINGLE_SUCCESS;
                            })));
        });
    }
}
