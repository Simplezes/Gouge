package net.gouge;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Gouge.MOD_ID)
public class Gouge {
    public static final String MOD_ID = "gouge";

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);
    private static final RegistryObject<Enchantment> GRIP = ENCHANTMENTS.register("grip", GougeEnchantment::new);
    private static final RegistryObject<Enchantment> MOMENTUM = ENCHANTMENTS.register("momentum", GougeEnchantment::new);

    public Gouge() {
        GougePlatform.set(new ForgePlatform());
        GougeConfig.load();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENCHANTMENTS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        if (ModList.get().isLoaded("parcool")) {
            net.gouge.extern.parcool.ParCoolCompat.register();
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        GougeEnchantments.GRIP = GRIP.get();
        GougeEnchantments.MOMENTUM = MOMENTUM.get();
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
