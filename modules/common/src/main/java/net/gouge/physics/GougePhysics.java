package net.gouge.physics;

import net.gouge.config.GougeConfig;
import net.gouge.platform.GougePlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static net.gouge.physics.BlockRules.*;
import static net.gouge.physics.ClimbMoves.*;
import static net.gouge.physics.GripEffects.*;
import static net.gouge.physics.GripMotion.*;
import static net.gouge.physics.GripState.*;
import static net.gouge.physics.Pickaxes.*;
import static net.gouge.physics.WallGeometry.*;
import static net.gouge.physics.WallRaycast.*;

public final class GougePhysics {

    private GougePhysics() {
    }

    public static boolean isJumpHeld(ServerPlayer player) {
        return player.getLastClientInput().jump() || player.isJumping();
    }

    public static float strafeIntent(ServerPlayer player) {
        var in = player.getLastClientInput();
        return in.left() == in.right() ? 0.0F : (in.left() ? 1.0F : -1.0F);
    }

    public static void noteReleaseAtLedge(ServerPlayer player) {
        UUID id = player.getUUID();
        ClimbPin saved = climbPins.get(id);
        int[] d = hangData.get(id);
        if (saved == null || d == null || saved.grabTick() != d[5] || !isJumpHeld(player)) {
            return;
        }
        boolean climbAllowed = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || isDualGripActive(player));
        if (!climbAllowed) {
            return;
        }
        Level world = player.level();
        BlockPos ledge = climbLedge(world, saved.pos(), player.getY());
        if (ledge == null) {
            return;
        }
        BlockState ledgeState = world.getBlockState(ledge);
        if (canStandOnLedge(world, ledge, ledgeState)
                && player.getY() >= ledgeTopY(world, ledge, ledgeState) - 1.0) {
            mantled.put(id, serverTick(player));
        }
    }

    public static InteractionHand handOverHand(ServerPlayer player) {
        InteractionHand was = activeHand.get(player.getUUID());
        if (was == null || !activeUse.contains(player.getUUID()) || isPickaxe(player.getItemInHand(was))) {
            return null;
        }
        InteractionHand other = was == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return isPickaxe(player.getItemInHand(other)) ? other : null;
    }

    public static void releaseCooldown(ServerPlayer player) {
        int ticks = (int) (GougeConfig.INSTANCE.mechanics.release_cooldown * 20);
        if (ticks <= 0 || grounded(player) || hasMantled(player)) {
            return;
        }
        for (ItemStack s : new ItemStack[]{activeStack.getOrDefault(player.getUUID(), ItemStack.EMPTY), player.getMainHandItem(), player.getOffhandItem()}) {
            if (isPickaxe(s) && !player.getCooldowns().isOnCooldown(s)) {
                player.getCooldowns().addCooldown(s, ticks);
            }
        }
    }

    public static boolean blocksOtherHand(Level world, Player player, InteractionHand hand) {
        if (!GougeConfig.INSTANCE.mechanics.block_other_hand) {
            return false;
        }
        ItemStack pick = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!isPickaxe(pick) || isPickaxe(player.getItemInHand(hand))) {
            return false;
        }
        if (!(player instanceof ServerPlayer) && GougeClientPhysics.isGripping()) {
            return true;
        }
        if (player instanceof ServerPlayer sp) {
            if (isActive(sp.getUUID())) {
                return true;
            }
            if (hasMantled(sp) || GougePlatform.get().blocksGouge(sp.getUUID())) {
                return false;
            }
        }
        if (grounded(player) || player.getDeltaMovement().y >= 0 || player.isSpectator() || player.getAbilities().flying
                || player.getCooldowns().isOnCooldown(pick)
                || !hasFallClearance(world, player, GougeConfig.INSTANCE.mechanics.min_fall_distance)) {
            return false;
        }
        BlockHitResult hit = raycast(world, player);
        return hit != null && world.getBlockState(hit.getBlockPos()).getDestroySpeed(world, hit.getBlockPos()) >= 0;
    }

    public static boolean grounded(Player player) {
        return player.onGround() || !player.level().noCollision(player, player.getBoundingBox().move(0, -0.05, 0));
    }

    public static boolean modStamina() {
        return GougeConfig.INSTANCE.mechanics.use_mod_stamina && GougePlatform.get().hasModStamina();
    }

    public static void checkStale(ServerPlayer player) {
        UUID id = player.getUUID();
        int[] assist = launchAssist.get(id);
        if (assist != null) {
            if (grounded(player) || --assist[0] <= 0) {
                launchAssist.remove(id);
            } else {
                puff(player.level(), ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
                        2, 0.15, 0.05, 0.15, 0.01);
                Vec3 push = assistPush(player.level(), player, Direction.from3DDataValue(assist[1]));
                if (push != null) {
                    Vec3 v = player.getDeltaMovement();
                    player.setDeltaMovement(push.x, v.y, push.z);
                    player.syncVelocity = true;
                }
            }
        }
        if (player.onGround() && mantled.containsKey(id)) {
            if (isJumpHeld(player)) {
                mantled.put(id, serverTick(player));
            } else {
                mantled.remove(id);
            }
        }
        if (!activeUse.contains(id)) {
            slideVy.remove(id);
            int[] d = hangData.get(id);
            if (d != null) {
                d[0] = -1;
                d[2] = -1;
                d[3] = 0;
                d[4] = 0;
                d[5] = -1;
                d[6] = 0;
                d[7] = 0;
            }
            if (trails.containsKey(id)) {
                clearCrack(player);
            }
            if (gougeNoGravity.contains(id)) {
                player.setNoGravity(false);
                gougeNoGravity.remove(id);
            }
            return;
        }
        if (!player.isUsingItem() || !isPickaxe(player.getUseItem())) {
            InteractionHand next = handOverHand(player);
            if (next != null) {
                player.startUsingItem(next);
                activeHand.put(id, next);
                activeStack.put(id, player.getItemInHand(next));
                return;
            }
            stopSliding(player);
            releaseCooldown(player);
        }
    }

    public static void stopSliding(ServerPlayer player) {
        int[] d = hangData.get(player.getUUID());
        if (d != null) {
            d[0] = -1;
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
            d[5] = -1;
            d[6] = 0;
            d[7] = 0;
        }
        release(player);
        activeUse.remove(player.getUUID());
    }

    static void release(ServerPlayer player) {
        player.setNoGravity(false);
        releaseHang(player.getUUID());
        clearCrack(player);
        anchors.remove(player.getUUID());
        lastY.remove(player.getUUID());
        launchCharge.remove(player.getUUID());
    }

    static int serverTick(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    static EquipmentSlot usedSlot(ServerPlayer player) {
        return player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }

    public static boolean tick(ServerPlayer player) {
        int[] d = data(player.getUUID());
        boolean result = tick0(player, d);
        if (!result) {
            d[0] = -1;
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
            d[5] = -1;
            d[6] = 0;
            d[7] = 0;
        }
        return result;
    }

    static boolean tick0(ServerPlayer player, int[] d) {
        if (grounded(player) || player.isSpectator() || player.getAbilities().flying) {
            release(player);
            return false;
        }

        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty() || !isPickaxe(useItem)) {
            release(player);
            return false;
        }

        double maxDrift = GougeConfig.INSTANCE.mechanics.max_drift;
        if (maxDrift > 0) {
            Vec3 anchor = anchors.get(player.getUUID());
            if (anchor != null) {
                double dx = player.getX() - anchor.x;
                double dz = player.getZ() - anchor.z;
                if (dx * dx + dz * dz > maxDrift * maxDrift) {
                    release(player);
                    return false;
                }
            }
        }

        ServerLevel world = player.level();
        BlockHitResult hit = raycast(world, player);
        ClimbPin savedPin = climbPins.get(player.getUUID());
        boolean climbAllowed = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || isDualGripActive(player));
        boolean shimmy = traverseAllowed(player) && strafeIntent(player) != 0 && !(climbAllowed && isJumpHeld(player));
        if (savedPin != null && savedPin.grabTick() == d[5] && ((climbAllowed && (hit == null || isJumpHeld(player))) || shimmy)) {
            BlockHitResult pinned = pinnedHit(world, climbFocus(world, savedPin.pos(), player.getY()), savedPin.face());
            if (pinned != null) {
                hit = pinned;
            }
        }
        if (hit == null) {
            release(player);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        var dir = hit.getDirection();
        if (!dir.getAxis().isHorizontal()) {
            release(player);
            return false;
        }

        BlockState state = world.getBlockState(pos);
        float rawHardness = state.getDestroySpeed(world, pos);
        if (rawHardness < 0) {
            release(player);
            return false;
        }
        float hardness = Math.max(0f, rawHardness);

        boolean isHoldingSpace = isJumpHeld(player);
        if (!isHoldingSpace && canStandOnLedge(world, pos, state)
                && player.getY() >= ledgeTopY(world, pos, state) - 0.05) {
            release(player);
            return false;
        }

        int now = serverTick(player);
        boolean sneakingNow = player.isShiftKeyDown();
        boolean wasSneaking = d[3] == 1;
        d[3] = sneakingNow ? 1 : 0;

        if (d[1] != -1 && now < d[1]) {
            release(player);
            return false;
        }

        if (wasSneaking && !sneakingNow) {
            d[2] = now;
        }
        if (!wasSneaking && sneakingNow) {
            int doubleTapTicks = (int) (GougeConfig.INSTANCE.wall_jump.time_window * 20);
            if (d[2] != -1 && now - d[2] <= doubleTapTicks) {
                d[2] = -1;
                return wallKick(player, world, hit, d);
            }
        }

        double realVy = actualVerticalSpeed(player);
        UUID uid = player.getUUID();
        Double slide = slideVy.get(uid);
        double effVy = slide != null ? slide : realVy;
        double lockLimit = d[0] != -1 ? hangHoldSpeed() : HANG_LOCK_SPEED;

        if (isHardBlock(state)) {
            d[4] = 0;
            player.setNoGravity(true);
            gougeNoGravity.add(player.getUUID());
            Vec3 v = player.getDeltaMovement();

            if (Math.abs(effVy) < lockLimit) {
                slideVy.remove(uid);
                player.fallDistance = 0.0F;
                if (d[0] == -1) {
                    d[5] = now;
                    climbPins.put(player.getUUID(), new ClimbPin(now, pos, dir));
                    d[7] = Math.max(1, (int) Math.round(singlePickaxeHangTicks(useItem) * blockGripMultiplier(state)));
                    boolean isDual = isDualGripActive(player);
                    d[6] = isDual ? 1 : 0;
                    int hangTicks = d[7];
                    if (isDual) {
                        hangTicks = (int) Math.round(hangTicks * GougeConfig.INSTANCE.mechanics.dual_grip_multiplier);
                    }
                    d[0] = now + Math.max(1, hangTicks);
                } else if (d[6] == 1 && !isDualGripActive(player)) {
                    d[6] = 0;
                    d[0] = d[5] + d[7];
                }

                boolean gripSpent = modStamina()
                        ? GougePlatform.get().staminaExhausted(player)
                        : !GougeConfig.INSTANCE.mechanics.unlimited_grip && now >= d[0];
                if (gripSpent) {
                    startCooldown(player, d);
                    spawnSlip(world, hit.getLocation());
                    release(player);
                    return false;
                }

                boolean canClimb = GougeConfig.INSTANCE.climbing.enabled
                        && (!GougeConfig.INSTANCE.climbing.requires_dual || isDualGripActive(player));

                if (isHoldingSpace && canClimb) {
                    ClimbResult climbResult = handleClimb(player, world, hit, pos, state, now);
                    if (climbResult == ClimbResult.RELEASED) {
                        return false;
                    }
                    if (climbResult == ClimbResult.SUCCESS) {
                        updateClimbFx(player, world, hit, pos, state, hardness, now);
                        return true;
                    }
                }

                if (!isHoldingSpace || !canClimb) {
                    launchCharge.remove(uid);
                }
                Vec3 shimmyMove = shimmy ? traverseStep(player, world, d, hit, state, hardness, now) : Vec3.ZERO;
                player.setDeltaMovement(shimmyMove);
                player.syncVelocity = true;

                if (now % 20 == 0 && !drain(player)) {
                    release(player);
                    return false;
                }
                if (now % 20 == 10) {
                    sound(world, pos, SoundEvents.CHAIN_STEP, 0.4f, 0.8f + world.getRandom().nextFloat() * 0.2f);
                }
                return true;
            }

            d[0] = -1;
            d[5] = -1;
            d[6] = 0;
            d[7] = 0;
            double hardFriction = Mth.clamp(
                    HARD_FRICTION_BASE - hardness * HARD_FRICTION_SCALE, HARD_MIN_FRICTION, HARD_MAX_FRICTION);
            double slideSpeedY = (slide != null ? slide : Math.min(realVy, 0)) * hardFriction;
            slideVy.put(uid, slideSpeedY);
            player.setDeltaMovement(new Vec3(v.x * HORIZONTAL_DAMPING, slideSpeedY, v.z * HORIZONTAL_DAMPING));
            player.fallDistance = 0.0F;
            player.syncVelocity = true;
            updateClimbFx(player, world, hit, pos, state, hardness, now);
            return true;
        }

        d[0] = -1;
        d[5] = -1;
        d[6] = 0;
        d[7] = 0;
        Vec3 v = player.getDeltaMovement();

        if (realVy > 0.01) {
            slideVy.remove(uid);
            d[4] = 0;
            player.setNoGravity(false);
            gougeNoGravity.remove(player.getUUID());
            updateClimbFx(player, world, hit, pos, state, hardness, now);
            return true;
        }

        gougeNoGravity.add(player.getUUID());
        player.setNoGravity(true);
        d[4] = 1;

        if (now % 20 == 0 && !drain(player)) {
            release(player);
            return false;
        }

        double slideSpeed = Mth.clamp(
                SOFT_SLIDE_MAX_SPEED - hardness * SOFT_SLIDE_HARDNESS_SCALE, SOFT_SLIDE_MIN_SPEED, SOFT_SLIDE_MAX_SPEED);
        double softVy = softSlideVy(slide != null ? slide : Math.min(realVy, 0), slideSpeed);
        slideVy.put(uid, softVy);
        player.setDeltaMovement(new Vec3(v.x * HORIZONTAL_DAMPING, softVy, v.z * HORIZONTAL_DAMPING));
        player.syncVelocity = true;

        updateClimbFx(player, world, hit, pos, state, hardness, now);
        return true;
    }
}
