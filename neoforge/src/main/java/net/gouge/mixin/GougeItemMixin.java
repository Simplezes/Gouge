package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

/**
 * Mixed into ItemStack (not Item) so this still fires for modded tools whose Item subclass
 * overrides use()/getUseDuration()/onUseTick()/releaseUsing() directly (e.g. Tinkers'
 * Construct's ModifiableItem) - ItemStack's versions are plain forwarding methods that are
 * never overridden by mods, so they always run before dispatching to the Item.
 */
@Mixin(ItemStack.class)
public abstract class GougeItemMixin {

    @Unique private static final int GOUGE_MAX_USE_TICKS = 72000;

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void gouge$getUseDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (GougePhysics.isPickaxe(self)) {
            cir.setReturnValue(GOUGE_MAX_USE_TICKS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void gouge$use(Level world, Player user, InteractionHand hand,
                           CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClientSide || !GougePhysics.isPickaxe(self)) return;
        if (user.onGround() || user.getDeltaMovement().y >= 0) return;

        BlockHitResult hit = GougePhysics.raycast(world, user);
        if (hit == null) return;

        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0) return;

        double downwardSpeed = Math.abs(user.getDeltaMovement().y);
        boolean survived = GougePhysics.applyImpactDamage(
                self, (ServerLevel) world, (ServerPlayer) user, downwardSpeed, hardness);
        if (!survived) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        GougePhysics.spawnImpact((ServerLevel) world, hit, state, hardness);
        user.startUsingItem(hand);
        GougePhysics.markActive(user.getUUID());
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void gouge$onUseTick(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClientSide || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!GougePhysics.isActive(player.getUUID())) return;
        if (!GougePhysics.tick(player)) {
            GougePhysics.clearActive(player.getUUID());
            player.stopUsingItem();
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void gouge$releaseUsing(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClientSide || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!GougePhysics.isActive(player.getUUID())) return;
        GougePhysics.stopSliding(player);
    }
}
