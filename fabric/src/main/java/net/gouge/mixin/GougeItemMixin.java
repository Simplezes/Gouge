package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class GougeItemMixin {

    @Unique private static final int GOUGE_MAX_USE_TICKS = 72000;

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void gouge$maxUseTime(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (GougePhysics.isPickaxe(self)) {
            cir.setReturnValue(GOUGE_MAX_USE_TICKS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void gouge$use(World world, PlayerEntity user, Hand hand,
                           CallbackInfoReturnable<ActionResult> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClient || !GougePhysics.isPickaxe(self)) return;
        if (user.isOnGround() || user.getVelocity().y >= 0) return;

        BlockHitResult hit = GougePhysics.raycast(world, user);
        if (hit == null) return;

        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness < 0) return;

        double downwardSpeed = Math.abs(user.getVelocity().y);
        boolean survived = GougePhysics.applyImpactDamage(
                self, (ServerWorld) world, (ServerPlayerEntity) user, downwardSpeed, hardness);
        if (!survived) {
            cir.setReturnValue(ActionResult.FAIL);
            return;
        }

        GougePhysics.spawnImpact((ServerWorld) world, hit, state, hardness);
        user.setCurrentHand(hand);
        GougePhysics.markActive(user.getUuid());
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void gouge$usageTick(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClient || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!GougePhysics.isActive(player.getUuid())) return;
        if (!GougePhysics.tick(player)) {
            GougePhysics.clearActive(player.getUuid());
            player.stopUsingItem();
        }
        ci.cancel();
    }

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void gouge$onStoppedUsing(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClient || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!GougePhysics.isActive(player.getUuid())) return;
        GougePhysics.stopSliding(player);
        ci.cancel();
    }
}
