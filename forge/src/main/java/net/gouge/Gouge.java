package net.gouge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Gouge.MOD_ID)
public class Gouge {
    public static final String MOD_ID = "gouge";

    public Gouge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        GougeEnchantments.ENCHANTMENTS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        if (ModList.get().isLoaded("parcool")) {
            net.gouge.extern.parcool.ParCoolCompat.register();
        }
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GougePhysics.cleanup(player.getUUID());
        }
    }
}
