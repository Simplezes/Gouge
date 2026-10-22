package net.gouge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class GougeClientPhysics {

    private static boolean predicting;
    private static Vec3 anchor;
    private static boolean hangLocked;
    private static int mantledUntil;
    private static BlockPos pin;
    private static Direction pinDir;

    private GougeClientPhysics() {
    }

    public static void clientTick(Player player) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local == null || player != local) {
            return;
        }
        if (local.onGround() && mantledUntil != 0) {
            mantledUntil = mc.options.keyJump.isDown() ? local.tickCount + GougePhysics.MANTLE_REGRAB_TICKS : 0;
        }
        if (mc.screen != null || !mc.options.keyUse.isDown()) {
            stop(local);
            return;
        }
        if (local.tickCount < mantledUntil || local.onGround() || local.isSpectator() || local.getAbilities().flying) {
            stop(local);
            return;
        }
        if (!predicting && !GougePhysics.hasFallClearance(local.level(), local, GougeConfig.INSTANCE.mechanics.min_fall_distance)) {
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

        local.connection.send(new ServerboundPlayerInputPacket(
                local.input.leftImpulse, local.input.forwardImpulse, local.input.jumping, local.input.shiftKeyDown));

        BlockHitResult hit = GougePhysics.raycast(local.level(), local);
        if (predicting && pin != null && pinDir != null && mc.options.keyJump.isDown()
                && GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || GougePhysics.isDualGripActive(local))) {
            BlockHitResult pinned = GougePhysics.pinnedHit(
                    local.level(), GougePhysics.climbFocus(local.level(), pin, local.getY()), pinDir);
            if (pinned != null) {
                hit = pinned;
            }
        }
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
            if (Math.abs(v.y) < (hangLocked ? GougePhysics.hangHoldSpeed() : GougePhysics.HANG_LOCK_SPEED)) {
                hangLocked = true;
                local.fallDistance = 0.0F;
                var dir = hit.getDirection();
                boolean canStand = GougePhysics.canStandOnLedge(local.level(), hit.getBlockPos(), state);
                double ledgeTopY = GougePhysics.ledgeTopY(local.level(), hit.getBlockPos(), state);

                boolean isHoldingSpace = mc.options.keyJump.isDown();
                boolean canClimb = GougeConfig.INSTANCE.climbing.enabled
                        && (!GougeConfig.INSTANCE.climbing.requires_dual || GougePhysics.isDualGripActive(local));

                if (pin == null && dir.getAxis().isHorizontal()) {
                    pin = hit.getBlockPos();
                    pinDir = dir;
                }
                if (isHoldingSpace && canClimb && pin != null) {
                    Direction face = pinDir;
                    pin = GougePhysics.trackWall(local.level(), pin, face, local.position(),
                            GougeConfig.INSTANCE.climbing.allow_diagonal
                                    && mc.options.keyLeft.isDown() != mc.options.keyRight.isDown());
                    BlockPos ledge = GougePhysics.climbLedge(local.level(), pin, local.getY());
                    BlockState ledgeState = ledge == null ? null : local.level().getBlockState(ledge);
                    if (ledge == null
                            || GougePhysics.wallDistance(pin, face, local.position()) > GougeConfig.INSTANCE.climbing.reach
                            || !GougePhysics.isHardBlock(ledgeState)) {
                        local.setDeltaMovement(Vec3.ZERO);
                        return;
                    }
                    if (GougePhysics.canStandOnLedge(local.level(), ledge, ledgeState)
                            && local.getY() >= GougePhysics.ledgeTopY(local.level(), ledge, ledgeState) - 0.45) {
                        local.setDeltaMovement(GougePhysics.mantleVelocity(local, face, ledge));
                        stop(local);
                        mantledUntil = local.tickCount + GougePhysics.MANTLE_REGRAB_TICKS;
                        return;
                    }

                    if (GougePhysics.climbCapped(local.level(), ledge, local.position(), local.getY())) {
                        local.setDeltaMovement(Vec3.ZERO);
                        return;
                    }

                    double speed = GougeConfig.INSTANCE.climbing.speed;
                    double dy = speed;
                    double dx = 0.0;
                    double dz = 0.0;

                    if (GougeConfig.INSTANCE.climbing.allow_diagonal) {
                        float leftIntent = mc.options.keyLeft.isDown() == mc.options.keyRight.isDown() ? 0.0F
                                : (mc.options.keyLeft.isDown() ? 1.0F : -1.0F);
                        dx = face.getStepZ() * leftIntent * (speed * 0.707);
                        dz = -face.getStepX() * leftIntent * (speed * 0.707);
                    }

                    if (local.level().noCollision(local, local.getBoundingBox().move(dx, dy, dz))) {
                        local.setDeltaMovement(new Vec3(dx, dy, dz));
                        if ((dx != 0 || dz != 0) && anchor != null) {
                            anchor = anchor.add(dx, 0, dz);
                        }
                    } else {
                        local.setDeltaMovement(Vec3.ZERO);
                    }
                } else {
                    if (canStand && local.getY() >= ledgeTopY - 0.05) {
                        stop(local);
                        return;
                    }
                    local.setDeltaMovement(Vec3.ZERO);
                }
            } else {
                hangLocked = false;
                double friction = Mth.clamp(
                        GougePhysics.HARD_FRICTION_BASE - hardness * GougePhysics.HARD_FRICTION_SCALE,
                        GougePhysics.HARD_MIN_FRICTION, GougePhysics.HARD_MAX_FRICTION);
                local.setDeltaMovement(new Vec3(
                        v.x * GougePhysics.HORIZONTAL_DAMPING, v.y * friction, v.z * GougePhysics.HORIZONTAL_DAMPING));
                local.fallDistance = 0.0F;
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
        hangLocked = false;
        pin = null;
        pinDir = null;
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
