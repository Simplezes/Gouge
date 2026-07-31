package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class GougeItemMixin {

    @Unique private static final int GOUGE_MAX_USE_TICKS = 72000;

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void gouge$getUseDuration(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (GougePhysics.isPickaxe(stack)) {
            cir.setReturnValue(GOUGE_MAX_USE_TICKS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void gouge$use(Level world, Player user, InteractionHand hand,
                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide || !GougePhysics.isPickaxe(user.getItemInHand(hand))) return;
        if (user.onGround() || user.getDeltaMovement().y >= 0) return;

        BlockHitResult hit = GougePhysics.raycast(world, user);
        if (hit == null) return;

        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0) return;

        ItemStack stack = user.getItemInHand(hand);
        double downwardSpeed = Math.abs(user.getDeltaMovement().y);
        boolean survived = GougePhysics.applyImpactDamage(
                stack, (ServerLevel) world, (ServerPlayer) user, downwardSpeed, hardness);
        if (!survived) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }

        GougePhysics.spawnImpact((ServerLevel) world, hit, state, hardness);
        user.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.success(stack));
    }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void gouge$onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide || !GougePhysics.isPickaxe(stack)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!GougePhysics.tick(player)) player.stopUsingItem();
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void gouge$releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide || !GougePhysics.isPickaxe(stack)) return;
        if (!(user instanceof ServerPlayer player)) return;
        GougePhysics.stopSliding(player);
    }
}
