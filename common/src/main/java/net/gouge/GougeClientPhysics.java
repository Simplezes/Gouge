package net.gouge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class GougeClientPhysics {

    private static boolean predicting;
    private static Vec3 anchor;

    private GougeClientPhysics() {
    }

    public static void clientTick(Player player) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local == null || player != local) {
            return;
        }
        if (mc.screen != null || !mc.options.keyUse.isDown()) {
            stop(local);
            return;
        }
        if (local.onGround() || local.isSpectator() || local.getAbilities().flying) {
            stop(local);
            return;
        }
        if (!predicting && local.fallDistance < GougeConfig.INSTANCE.mechanics.min_fall_distance) {
            stop(local);
            return;
        }

        ItemStack stack = local.getMainHandItem();
        if (!GougePhysics.isPickaxe(stack)) {
            stack = local.getOffhandItem();
            if (!GougePhysics.isPickaxe(stack)) {
                stop(local);
                return;
            }
        }
        if (local.getCooldowns().isOnCooldown(stack.getItem())) {
            stop(local);
            return;
        }

        Vec3 v = local.getDeltaMovement();
        if (!predicting && v.y >= 0) {
            stop(local);
            return;
        }

        double maxDrift = GougeConfig.INSTANCE.mechanics.max_drift;
        if (predicting && anchor != null && maxDrift > 0) {
            double dx = local.getX() - anchor.x;
            double dz = local.getZ() - anchor.z;
            if (dx * dx + dz * dz > maxDrift * maxDrift) {
                stop(local);
                return;
            }
        }

        BlockHitResult hit = GougePhysics.raycast(local.level(), local);
        if (hit == null) {
            stop(local);
            return;
        }

        BlockState state = local.level().getBlockState(hit.getBlockPos());
        float rawHardness = state.getDestroySpeed(local.level(), hit.getBlockPos());
        if (rawHardness < 0) {
            stop(local);
            return;
        }
        if (!GougeConfig.INSTANCE.mechanics.client_prediction) {
            start(local);
            return;
        }
        float hardness = Math.max(0f, rawHardness);

        if (GougePhysics.isHardBlock(state)) {
            start(local);
            local.setNoGravity(true);
            if (Math.abs(v.y) < GougePhysics.HANG_LOCK_SPEED) {
                local.fallDistance = 0.0F;
                local.setDeltaMovement(Vec3.ZERO);
            } else {
                double friction = Mth.clamp(
                        GougePhysics.HARD_FRICTION_BASE - hardness * GougePhysics.HARD_FRICTION_SCALE,
                        GougePhysics.HARD_MIN_FRICTION, GougePhysics.HARD_MAX_FRICTION);
                local.setDeltaMovement(new Vec3(
                        v.x * GougePhysics.HORIZONTAL_DAMPING, v.y * friction, v.z * GougePhysics.HORIZONTAL_DAMPING));
            }
            return;
        }

        if (v.y >= 0) {
            start(local);
            local.setNoGravity(false);
            return;
        }

        start(local);
        local.setNoGravity(true);
        double slideSpeed = Mth.clamp(
                GougePhysics.SOFT_SLIDE_MAX_SPEED - hardness * GougePhysics.SOFT_SLIDE_HARDNESS_SCALE,
                GougePhysics.SOFT_SLIDE_MIN_SPEED, GougePhysics.SOFT_SLIDE_MAX_SPEED);
        local.setDeltaMovement(new Vec3(
                v.x * GougePhysics.HORIZONTAL_DAMPING, -slideSpeed, v.z * GougePhysics.HORIZONTAL_DAMPING));
    }

    private static void start(LocalPlayer local) {
        predicting = true;
        if (anchor == null) {
            anchor = local.position();
        }
    }

    private static void stop(LocalPlayer local) {
        anchor = null;
        if (!predicting) {
            return;
        }
        predicting = false;
        local.setNoGravity(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(local);
        }
    }
}
