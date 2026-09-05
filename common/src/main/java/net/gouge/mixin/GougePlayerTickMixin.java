package net.gouge.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class GougePlayerTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void gouge$clientTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) {
            net.gouge.GougeClientPhysics.clientTick(self);
        }
    }
}
