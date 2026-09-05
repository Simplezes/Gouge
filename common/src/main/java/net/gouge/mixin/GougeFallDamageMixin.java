package net.gouge.mixin;

import net.gouge.GougeConfig;
import net.gouge.GougePhysics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GougeFallDamageMixin {

    @Inject(method = "calculateFallDamage", at = @At("RETURN"), cancellable = true)
    private void gouge$calculateFallDamage(double fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        float multiplier = GougePhysics.fallDamageMultiplier(player.getUUID());
        if (multiplier != 1.0f) {
            int damage = Math.round(cir.getReturnValue() * multiplier);
            double cap = GougeConfig.INSTANCE.mechanics.soft_fall_damage_cap;
            if (cap > 0) {
                damage = (int) Math.min(damage, cap);
            }
            cir.setReturnValue(damage);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void gouge$die(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            GougePhysics.cleanup(player.getUUID());
        }
    }
}
