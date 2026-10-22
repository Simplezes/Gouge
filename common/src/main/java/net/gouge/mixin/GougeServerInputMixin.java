package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GougeServerInputMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerInput", at = @At("HEAD"))
    private void gouge$recordInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        GougePhysics.recordInput(player, packet.getXxa(), packet.isJumping());
    }
}
