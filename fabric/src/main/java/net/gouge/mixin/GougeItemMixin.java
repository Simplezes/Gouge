package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixed into ItemStack (not Item) so this still fires for modded tools whose Item subclass
 * overrides use()/getMaxUseTime()/usageTick()/onStoppedUsing() directly (e.g. Tinkers'
 * Construct's ModifiableItem) - ItemStack's versions are plain forwarding methods that are
 * never overridden by mods, so they always run before dispatching to the Item.
 */
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
                           CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
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
            cir.setReturnValue(TypedActionResult.fail(self));
            return;
        }

        GougePhysics.spawnImpact((ServerWorld) world, hit, state, hardness);
        user.setCurrentHand(hand);
        GougePhysics.markActive(user.getUuid());
        cir.setReturnValue(TypedActionResult.success(self));
    }

    @Inject(method = "usageTick", at = @At("HEAD"))
    private void gouge$usageTick(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClient || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!GougePhysics.isActive(player.getUuid())) return;
        if (!GougePhysics.tick(player)) player.stopUsingItem();
    }

    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void gouge$onStoppedUsing(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (world.isClient || !GougePhysics.isPickaxe(self)) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!GougePhysics.isActive(player.getUuid())) return;
        GougePhysics.stopSliding(player);
    }
}
