package net.gouge.physics;

import net.gouge.config.ConfigData;
import net.gouge.config.GougeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.gouge.physics.BlockRules.*;

public final class GripEffects {

    private GripEffects() {
    }

    static final int CRACK_SLOT_STRIDE = 32;
    static final Map<UUID, ArrayDeque<BlockPos>> trails = new ConcurrentHashMap<>();

    public static void clearCrack(ServerPlayer player) {
        ArrayDeque<BlockPos> trail = trails.remove(player.getUUID());
        if (trail != null) {
            int slot = 0;
            for (BlockPos p : trail) {
                sendCrack(player, p, -1, slot++);
            }
        }
    }

    static void sendCrack(ServerPlayer player, BlockPos pos, int stage, int slot) {
        int entityId = -1 - (player.getId() * CRACK_SLOT_STRIDE + slot);
        player.connection.send(new ClientboundBlockDestructionPacket(entityId, pos, stage));
    }

    static boolean isMetal(BlockState state) {
        SoundType t = state.getSoundType();
        return t == SoundType.METAL || t == SoundType.COPPER || t == SoundType.NETHERITE_BLOCK || t == SoundType.ANVIL;
    }

    static boolean isDusty(BlockState state) {
        SoundType t = state.getSoundType();
        return t == SoundType.SAND || t == SoundType.GRAVEL || t == SoundType.SNOW;
    }

    static ParticleOptions sparks(BlockState state) {
        return isMetal(state) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT;
    }

    static void warnLowDurability(ServerLevel world, ServerPlayer player, int now) {
        ItemStack stack = player.getUseItem();
        int max = stack.getMaxDamage();
        if (now % 20 != 0 || max <= 0 || max - stack.getDamageValue() > Math.max(8, max / 10)) {
            return;
        }
        sound(world, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), 0.5f, 1.6f);
    }

    static final Map<UUID, Integer> clingAt = new ConcurrentHashMap<>();

    static void cling(ServerLevel world, ServerPlayer player, int[] d, int now) {
        boolean timed = !GougePhysics.modStamina() && !GougeConfig.INSTANCE.mechanics.unlimited_grip;
        float left = timed ? (float) (d[0] - now) / Math.max(1, d[0] - d[5]) : 1f;
        float urgency = left >= 0.5f ? 0f : 1f - left / 0.5f;
        int last = Math.max(clingAt.getOrDefault(player.getUUID(), d[5]), d[5]);
        if (now - last < Math.round(20 - 10 * urgency)) {
            return;
        }
        clingAt.put(player.getUUID(), now);
        sound(world, player.blockPosition(), SoundEvents.CHAIN_STEP, 0.4f + 0.2f * urgency,
                0.8f + 0.5f * urgency + world.getRandom().nextFloat() * 0.2f);
    }

    public static void spawnImpact(ServerLevel world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3 p = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundType();
        if (isHardBlock(state)) {
            sound(world, pos, sounds.getBreakSound(), 2.0f, 0.5f);
            puff(world, sparks(state), p.x, p.y, p.z, 20, 0.2, 0.2, 0.2, 0.15);
        } else {
            sound(world, pos, sounds.getBreakSound(), 1.5f, 0.6f);
            puff(world, new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 25, 0.3, 0.3, 0.3, 0.0);
            if (isDusty(state)) {
                puff(world, ParticleTypes.POOF, p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    static void sound(ServerLevel world, BlockPos pos, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        ConfigData.Effects fx = GougeConfig.INSTANCE.effects;
        float v = volume * (float) fx.volume;
        if (fx.sounds && v > 0) {
            world.playSound(null, pos, event, SoundSource.PLAYERS, v, pitch);
        }
    }

    static void puff(ServerLevel world, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        if (GougeConfig.INSTANCE.effects.particles) {
            world.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    static void chips(ServerLevel world, ServerPlayer player, Direction face, BlockState state, int count, double speed) {
        Vec3 at = player.position().add(0, 1.0, 0).subtract(face.getStepX() * 0.45, 0, face.getStepZ() * 0.45);
        puff(world, new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, count, 0.25, 0.35, 0.25, speed);
    }

    static void updateClimbFx(ServerPlayer player, ServerLevel world, BlockHitResult hit,
            BlockPos pos, BlockState state, float hardness, int now) {
        warnLowDurability(world, player, now);
        int trailLength = GougeConfig.INSTANCE.mechanics.trail_length;
        if (trailLength <= 0) {
            if (now % 5 == 0) {
                spawnJuice(world, hit, state, hardness);
            }
            return;
        }
        ArrayDeque<BlockPos> trail = trails.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        if (trail.isEmpty() || !trail.peekFirst().equals(pos)) {
            trail.addFirst(pos);
            while (trail.size() > trailLength) {
                sendCrack(player, trail.removeLast(), -1, trail.size());
            }
            int stage = 5;
            int slot = 0;
            for (BlockPos trailPos : trail) {
                sendCrack(player, trailPos, stage, slot++);
                if (stage > 1) {
                    stage--;
                }
            }
        }

        if (now % 5 == 0) {
            spawnJuice(world, hit, state, hardness);
        }
    }

    static void spawnSlip(ServerLevel world, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        sound(world, pos, SoundEvents.ITEM_BREAK.value(), 1.0f, 1.4f);
        puff(world, ParticleTypes.CRIT, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.2);
    }

    static void spawnJuice(ServerLevel world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3 p = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundType();
        if (isHardBlock(state)) {
            sound(world, pos, sounds.getHitSound(),
                    0.6f, (isMetal(state) ? 1.1f : 0.6f) + world.getRandom().nextFloat() * 0.2f);
            puff(world, sparks(state), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.05);
            double ox = hit.getDirection().getStepX() * 0.15;
            double oz = hit.getDirection().getStepZ() * 0.15;
            puff(world, new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x + ox, p.y + 0.3, p.z + oz, 8, 0.05, 0.3, 0.05, 0.08);
        } else {
            sound(world, pos, sounds.getHitSound(), 0.5f, 1.0f);
            puff(world, new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.0);
            if (isDusty(state)) {
                puff(world, ParticleTypes.POOF, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }
    }
}
