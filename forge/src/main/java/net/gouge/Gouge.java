package net.gouge;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Gouge.MOD_ID)
public class Gouge {
    public static final String MOD_ID = "gouge";

    public Gouge() {
        GougePlatform.set(new ForgePlatform());
        GougeConfig.load();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isNoGravity()) {
            player.setNoGravity(false);
        }
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setNoGravity(false);
            GougePhysics.cleanup(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gouge")
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
    }
}
