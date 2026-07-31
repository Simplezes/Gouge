package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GougeFallDamageMixin {

    @Inject(method = "computeFallDamage", at = @At("RETURN"), cancellable = true)
    private void gouge$computeFallDamage(float fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        float multiplier = GougePhysics.fallDamageMultiplier(player.getUuid());
        if (multiplier != 1.0f) {
            cir.setReturnValue(Math.round(cir.getReturnValue() * multiplier));
        }
    }
}
