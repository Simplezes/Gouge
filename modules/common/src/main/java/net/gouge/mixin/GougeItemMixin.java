package net.gouge.mixin;

import net.gouge.config.GougeConfig;
import net.gouge.physics.GougePhysics;
import net.gouge.physics.GripEffects;
import net.gouge.physics.GripState;
import net.gouge.physics.Pickaxes;
import net.gouge.physics.WallRaycast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class GougeItemMixin {

    @Unique private static final int GOUGE_MAX_USE_TICKS = 72000;

    @Inject(method = "getUseDuration()I", at = @At("HEAD"), cancellable = true)
    private void gouge$getUseDuration(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (Pickaxes.isPickaxe(self) && self.getItem().getUseDuration(self) <= 0) {
            cir.setReturnValue(GOUGE_MAX_USE_TICKS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void gouge$use(Level world, Player user, InteractionHand hand,
                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (GougePhysics.blocksOtherHand(world, user, hand)) {
            cir.setReturnValue(InteractionResultHolder.fail(self));
            return;
        }
        if (world.isClientSide || !(world instanceof ServerLevel serverLevel)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!Pickaxes.isPickaxe(self)) return;
        if (GripState.isActive(player.getUUID())) return;
        if (GripState.hasMantled(player)) return;
        if (GougePhysics.grounded(player) || player.getDeltaMovement().y >= 0) return;
        if (!WallRaycast.hasFallClearance(world, player, GougeConfig.INSTANCE.mechanics.min_fall_distance)) return;
        if (player.isSpectator() || player.getAbilities().flying) return;
        if (net.gouge.platform.GougePlatform.get().blocksGouge(player.getUUID())) return;

        BlockHitResult hit = WallRaycast.raycast(world, player);
        if (hit == null) return;

        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0) return;

        double downwardSpeed = Math.abs(player.getDeltaMovement().y);
        boolean survived = Pickaxes.applyImpactDamage(self, serverLevel, player, downwardSpeed, hardness);
        if (!survived) {
            cir.setReturnValue(InteractionResultHolder.fail(self));
            return;
        }

        GripEffects.spawnImpact(serverLevel, hit, state, hardness);
        GripState.markActive(player.getUUID(), hand, self);
        GripState.setAnchor(player.getUUID(), player.position());
        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.consume(self));
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void gouge$onUseTick(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClientSide || !Pickaxes.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!GripState.isActive(player.getUUID())) return;
        if (!GougePhysics.tick(player)) {
            GripState.clearActive(player.getUUID());
            player.stopUsingItem();
        }
        ci.cancel();
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void gouge$releaseUsing(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClientSide || !Pickaxes.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!GripState.isActive(player.getUUID())) return;
        if (GougePhysics.handOverHand(player) == null) {
            GougePhysics.noteReleaseAtLedge(player);
            GougePhysics.stopSliding(player);
            GougePhysics.releaseCooldown(player);
        }
        ci.cancel();
    }
}
