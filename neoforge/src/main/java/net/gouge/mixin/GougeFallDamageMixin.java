package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GougeFallDamageMixin {

    @Inject(method = "calculateFallDamage", at = @At("RETURN"), cancellable = true)
    private void gouge$calculateFallDamage(float fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        float multiplier = GougePhysics.fallDamageMultiplier(player.getUUID());
        if (multiplier != 1.0f) {
            cir.setReturnValue(Math.round(cir.getReturnValue() * multiplier));
        }
    }
}
