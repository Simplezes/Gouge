package net.gouge;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class Gouge implements ModInitializer {
    public static final String MOD_ID = "gouge";

    @Override
    public void onInitialize() {
        GougePlatform.set(new FabricPlatform());
        GougeConfig.load();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.isNoGravity()) {
                handler.player.setNoGravity(false);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            handler.player.setNoGravity(false);
            GougePhysics.cleanup(handler.player.getUUID());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("gouge")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                            || (source.getServer() != null && source.getServer().isSingleplayer()
                                && source.getEntity() instanceof ServerPlayer player
                                && source.getServer().isSingleplayerOwner(player.nameAndId())))
                    .then(Commands.literal("reload")
                            .executes(context -> {
                                GougeConfig.load();
                                context.getSource().sendSuccess(() -> Component.literal("Gouge config reloaded!"), true);
                                return Command.SINGLE_SUCCESS;
                            })));
        });
    }
}
