package net.gouge.mixin;

import net.gouge.GougePhysics;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
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


@Mixin(Item.class)
public abstract class GougeItemMixin {

    @Unique private static final int GOUGE_MAX_USE_TICKS = 72000;

    @Inject(method = "getMaxUseTime(Lnet/minecraft/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void gouge$maxUseTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PickaxeItem) {
            cir.setReturnValue(GOUGE_MAX_USE_TICKS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void gouge$use(World world, PlayerEntity user, Hand hand,
                           CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!((Object) this instanceof PickaxeItem) || world.isClient) return;
        if (user.isOnGround() || user.getVelocity().y >= 0) return;

        BlockHitResult hit = GougePhysics.raycast(world, user);
        if (hit == null) return;

        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness < 0) return;

        ItemStack stack = user.getStackInHand(hand);
        double downwardSpeed = Math.abs(user.getVelocity().y);
        boolean survived = GougePhysics.applyImpactDamage(
                stack, (ServerWorld) world, (ServerPlayerEntity) user, downwardSpeed, hardness);
        if (!survived) {
            cir.setReturnValue(TypedActionResult.fail(stack));
            return;
        }

        GougePhysics.spawnImpact((ServerWorld) world, hit, state, hardness);
        user.setCurrentHand(hand);
        cir.setReturnValue(TypedActionResult.success(stack));
    }

    @Inject(method = "usageTick", at = @At("HEAD"))
    private void gouge$usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        if (!((Object) this instanceof PickaxeItem) || world.isClient) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!GougePhysics.tick(player)) player.stopUsingItem();
    }

    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void gouge$onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (!((Object) this instanceof PickaxeItem) || world.isClient) return;
        if (!(user instanceof ServerPlayerEntity player)) return;
        GougePhysics.stopSliding(player);
    }

}
