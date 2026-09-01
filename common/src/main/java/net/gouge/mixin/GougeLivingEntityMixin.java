package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class GougeLivingEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void gouge$tick(CallbackInfo ci) {
        GougePhysics.checkStale((ServerPlayer)(Object)this);
    }
}
