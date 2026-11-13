package net.gouge.physics;

import net.gouge.config.ConfigData;
import net.gouge.config.GougeConfig;
import net.gouge.enchant.GougeEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static net.gouge.physics.BlockRules.*;
import static net.gouge.physics.GougePhysics.*;
import static net.gouge.physics.GripEffects.*;
import static net.gouge.physics.GripMotion.*;
import static net.gouge.physics.GripState.*;
import static net.gouge.physics.Pickaxes.*;
import static net.gouge.physics.WallGeometry.*;

public final class ClimbMoves {

    private ClimbMoves() {
    }

    enum ClimbResult {
        SUCCESS,
        BLOCKED,
        RELEASED
    }

    static ClimbResult handleClimb(ServerPlayer player, ServerLevel world, BlockHitResult hit,
            BlockPos currentPos, BlockState currentState, int now) {
        UUID id = player.getUUID();
        int grabTick = data(id)[5];
        ClimbPin saved = climbPins.get(id);
        boolean pinned = saved != null && saved.grabTick() == grabTick;
        Direction dir = pinned ? saved.face() : hit.getDirection();
        if (!dir.getAxis().isHorizontal()) {
            release(player);
            return ClimbResult.RELEASED;
        }

        BlockPos pin = trackWall(world, pinned ? saved.pos() : currentPos, dir, player.position(),
                GougeConfig.INSTANCE.climbing.allow_diagonal && strafeIntent(player) != 0);
        climbPins.put(id, new ClimbPin(grabTick, pin, dir));

        if (wallDistance(pin, dir, player.position()) > GougeConfig.INSTANCE.climbing.reach) {
            return ClimbResult.BLOCKED;
        }
        BlockPos ledge = climbLedge(world, pin, player.getY());
        if (ledge == null) {
            return ClimbResult.BLOCKED;
        }
        BlockState ledgeState = world.getBlockState(ledge);
        if (!isHardBlock(ledgeState) || ledgeState.getDestroySpeed(world, ledge) < 0) {
            return ClimbResult.BLOCKED;
        }

        if (canStandOnLedge(world, ledge, ledgeState)
                && player.getY() >= ledgeTopY(world, ledge, ledgeState) - 0.45) {
            player.setDeltaMovement(mantleVelocity(player, dir, ledge));
            mantled.put(id, now);
            player.hurtMarked = true;
            player.setNoGravity(false);
            gougeNoGravity.remove(id);
            release(player);
            player.stopUsingItem();
            GripState.clearActive(id);
            sound(world, ledge, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.6f, 1.2f);
            return ClimbResult.RELEASED;
        }

        if (climbCapped(world, ledge, player.position(), player.getY())) {
            return ClimbResult.BLOCKED;
        }

        if (softAhead(world, pin, player.getY(), GougeConfig.INSTANCE.climbing.speed)) {
            return tryLaunch(player, world, dir, pin, now);
        }
        launchCharge.remove(id);
        double speed = GougeConfig.INSTANCE.climbing.speed;
        double dy = speed;
        double dx = 0.0;
        double dz = 0.0;

        if (GougeConfig.INSTANCE.climbing.allow_diagonal) {
            float leftIntent = strafeIntent(player);
            if (leftIntent != 0) {
                double tx = -dir.getStepZ();
                double tz = dir.getStepX();
                dx = tx * leftIntent * (speed * 0.707);
                dz = tz * leftIntent * (speed * 0.707);
            }
        }

        if (!world.noCollision(player, player.getBoundingBox().move(dx, dy, dz))) {
            return ClimbResult.BLOCKED;
        }

        BlockPos destBlockPos = ledge;
        Vec3 contact = player.position().add(0, 1.0, 0).subtract(dir.getStepX() * 0.4, 0, dir.getStepZ() * 0.4);

        player.setDeltaMovement(new Vec3(dx, dy, dz));
        player.hurtMarked = true;

        if (dx != 0 || dz != 0) {
            Vec3 anchor = anchors.get(player.getUUID());
            if (anchor != null) {
                anchors.put(player.getUUID(), anchor.add(dx, 0, dz));
            }
        }

        if (now % 20 == 0) {
            if (!drain(player)) {
                release(player);
                return ClimbResult.RELEASED;
            }
            int extraCost = GougeConfig.INSTANCE.climbing.durability_cost;
            if (extraCost > 0) {
                if (GougeConfig.INSTANCE.climbing.damage_both_tools && hasDualPickaxes(player)) {
                    ItemStack main = player.getMainHandItem();
                    ItemStack off = player.getOffhandItem();
                    main.hurtAndBreak(extraCost, player, EquipmentSlot.MAINHAND);
                    off.hurtAndBreak(extraCost, player, EquipmentSlot.OFFHAND);
                } else {
                    ItemStack stack = player.getUseItem();
                    stack.hurtAndBreak(extraCost, player, usedSlot(player));
                }
                if (player.getUseItem().isEmpty()) {
                    release(player);
                    return ClimbResult.RELEASED;
                }
            }
        }

        if (now % 5 == 0) {
            chips(world, player, dir, world.getBlockState(pin), 4, 0.05);
        }
        if (now % 10 == 0) {
            sound(world, destBlockPos, SoundEvents.CHAIN_STEP, 0.4f, 1.2f + world.getRandom().nextFloat() * 0.2f);
            puff(world, ParticleTypes.CRIT, contact.x, contact.y, contact.z,
                    2, 0.05, 0.05, 0.05, 0.05);
        }

        return ClimbResult.SUCCESS;
    }

    static boolean traverseAllowed(ServerPlayer player) {
        ConfigData.Traverse t = GougeConfig.INSTANCE.traverse;
        return t.enabled && (!t.requires_dual || isDualGripActive(player));
    }

    static Vec3 traverseStep(ServerPlayer player, ServerLevel world, int[] d, BlockHitResult hit,
            BlockState state, float hardness, int now) {
        UUID id = player.getUUID();
        ClimbPin saved = climbPins.get(id);
        if (saved == null || saved.grabTick() != d[5]) {
            return Vec3.ZERO;
        }
        Vec3 delta = traverseDelta(player, saved.face(), strafeIntent(player), player.getUseItem());
        BlockPos next = traverseCell(world, saved.pos(), saved.face(), player.position().add(delta.scale(4)), player.getY());
        if (next == null || !world.noCollision(player, player.getBoundingBox().move(delta))) {
            return Vec3.ZERO;
        }
        climbPins.put(id, new ClimbPin(saved.grabTick(), next, saved.face()));
        Vec3 anchor = anchors.get(id);
        if (anchor != null) {
            anchors.put(id, anchor.add(delta.x, 0, delta.z));
        }
        BlockState wall = world.getBlockState(next);
        BlockHitResult pinned = pinnedHit(world, next, saved.face());
        if (!next.equals(saved.pos())) {
            clearCrack(player);
        }
        if (pinned != null) {
            updateClimbFx(player, world, pinned, next, wall, Math.max(0f, wall.getDestroySpeed(world, next)), now);
        }
        if (now % 3 == 0) {
            chips(world, player, saved.face(), wall, 5, 0.04);
        }
        if (now % 5 == 0) {
            var snd = wall.getSoundType();
            sound(world, next, snd.getHitSound(), 0.7f, 0.75f + world.getRandom().nextFloat() * 0.3f);
        }
        int cost = GougeConfig.INSTANCE.traverse.durability_cost;
        if (cost > 0 && now % 20 == 0) {
            player.getUseItem().hurtAndBreak(cost, player, usedSlot(player));
        }
        return delta;
    }

    static ClimbResult tryLaunch(ServerPlayer player, ServerLevel world, Direction dir, BlockPos pin, int now) {
        ConfigData.Launch l = GougeConfig.INSTANCE.launch;
        UUID id = player.getUUID();
        BlockPos cap = l.enabled ? launchCap(world, pin, player.getY(), GougeConfig.INSTANCE.climbing.speed) : null;
        if (cap == null) {
            launchCharge.remove(id);
            return ClimbResult.BLOCKED;
        }
        BlockPos hands = handsCell(pin, player.getY(), GougeConfig.INSTANCE.climbing.speed);
        BlockState capState = world.getBlockState(hands);
        int charged = launchCharge.merge(id, 1, Integer::sum);
        if (charged < (int) (l.charge_time * 20)) {
            if (charged % 2 == 1) {
                chips(world, player, dir, capState, 4, 0.02);
            }
            if (charged % 4 == 1) {
                sound(world, hands, capState.getSoundType().getHitSound(), 0.8f, 0.55f);
            }
            return ClimbResult.BLOCKED;
        }
        launchCharge.remove(id);
        ItemStack stack = player.getUseItem();
        player.setDeltaMovement(launchVelocity(stack, launchRise(world, cap, player.getY())));
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        mantled.put(id, now);
        launchAssist.put(id, new int[]{LAUNCH_ASSIST_TICKS, dir.get3DDataValue()});
        player.setNoGravity(false);
        gougeNoGravity.remove(id);
        release(player);
        player.stopUsingItem();
        clearActive(id);
        Vec3 at = player.position().add(0, 1.0, 0).subtract(dir.getStepX() * 0.4, 0, dir.getStepZ() * 0.4);
        sound(world, hands, capState.getSoundType().getBreakSound(), 1.3f, 0.8f);
        sound(world, pin, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.4f);
        puff(world, new BlockParticleOption(ParticleTypes.BLOCK, capState), at.x, at.y, at.z, 30, 0.35, 0.3, 0.35, 0.12);
        puff(world, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 14, 0.25, 0.1, 0.25, 0.06);
        return ClimbResult.RELEASED;
    }

    static boolean drain(ServerPlayer player) {
        ItemStack stack = player.getUseItem();
        ConfigData.PickaxeStats stats = getStats(stack);
        if (stats.damage_rate > 0) {
            stack.hurtAndBreak(stats.damage_rate, player, usedSlot(player));
        }
        return !stack.isEmpty();
    }

    static boolean wallKick(ServerPlayer player, ServerLevel world, BlockHitResult hit, int[] d) {
        startCooldown(player, d);
        d[0] = -1;
        d[4] = 0;
        d[5] = -1;
        d[6] = 0;
        d[7] = 0;
        release(player);
        player.fallDistance = 0.0F;

        var dir = hit.getDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double spring = springScale(player.getUseItem());
        double kickH = GougeConfig.INSTANCE.wall_jump.forward_boost * spring;
        double kickV = GougeConfig.INSTANCE.wall_jump.upward_boost * spring;
        player.setDeltaMovement(normal.scale(kickH).add(0, kickV, 0));
        player.hurtMarked = true;

        sound(world, hit.getBlockPos(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
        puff(world, ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                15, 0.2, 0.2, 0.2, 0.25);
        chips(world, player, dir, world.getBlockState(hit.getBlockPos()), 20, 0.12);
        return true;
    }

    static void startCooldown(ServerPlayer player, int[] d) {
        ItemStack stack = player.getUseItem();
        int momentum = enchLevel(stack, GougeEnchantments.MOMENTUM);
        int baseCooldown = (int) (GougeConfig.INSTANCE.mechanics.slip_cooldown * 20);
        int reduction = (int) (GougeConfig.INSTANCE.enchantments.momentum_reduction * 20);
        int cooldown = Math.max(0, baseCooldown - momentum * reduction);
        d[1] = cooldown > 0 ? serverTick(player) + cooldown : -1;
        if (cooldown > 0) {
            for (InteractionHand h : InteractionHand.values()) {
                ItemStack s = player.getItemInHand(h);
                if (isPickaxe(s)) {
                    player.getCooldowns().addCooldown(s.getItem(), cooldown);
                }
            }
        }
    }
}
