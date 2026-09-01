package net.gouge;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Gouge.MOD_ID)
public class Gouge {
    public static final String MOD_ID = "gouge";

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, MOD_ID);
    private static final DeferredHolder<Enchantment, Enchantment> GRIP = ENCHANTMENTS.register("grip", GougeEnchantment::new);
    private static final DeferredHolder<Enchantment, Enchantment> MOMENTUM = ENCHANTMENTS.register("momentum", GougeEnchantment::new);

    public Gouge(IEventBus modEventBus) {
        GougePlatform.set(new NeoForgePlatform());
        GougeConfig.load();
        ENCHANTMENTS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        GougeEnchantments.GRIP = GRIP.get();
        GougeEnchantments.MOMENTUM = MOMENTUM.get();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isNoGravity()) {
            player.setNoGravity(false);
        }
    }

    private void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setNoGravity(false);
            GougePhysics.cleanup(player.getUUID());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gouge")
                .requires(source -> source.hasPermission(2)
                        || (source.getServer().isSingleplayer()
                            && source.getEntity() instanceof ServerPlayer player
                            && source.getServer().isSingleplayerOwner(player.getGameProfile())))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            GougeConfig.load();
                            context.getSource().sendSuccess(() -> Component.literal("Gouge config reloaded!"), true);
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
