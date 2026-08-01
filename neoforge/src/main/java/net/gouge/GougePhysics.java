package net.gouge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GougePhysics {
    private static final double RAYCAST_RANGE = 2.5;
    private static final double SOFT_SLIDE_MAX_SPEED = 0.6;
    private static final double SOFT_SLIDE_MIN_SPEED = 0.35;
    private static final double SOFT_SLIDE_HARDNESS_SCALE = 0.17;
    private static final double HORIZONTAL_DAMPING = 0.5;
    private static final float HARD_MATERIAL_HARDNESS = 1.5f;
    private static final double HARD_FRICTION_BASE = 0.90;
    private static final double HARD_FRICTION_SCALE = 0.03;
    private static final double HARD_MIN_FRICTION = 0.55;
    private static final double HARD_MAX_FRICTION = 0.90;
    private static final float SOFT_LANDING_DAMAGE_RETENTION = 0.5f;
    private static final double HANG_LOCK_SPEED = 0.08;
    private static final int HANG_COOLDOWN_TICKS = 100;
    private static final double WALLKICK_HORIZONTAL = 1.4;
    private static final double WALLKICK_VERTICAL = 1.1;
    private static final int DOUBLE_TAP_WINDOW_TICKS = 10;
    private static final int TRAIL_LENGTH = 5;

    private static final Map<UUID, int[]> hangData = new HashMap<>();
    private static final Map<UUID, ArrayDeque<BlockPos>> trails = new HashMap<>();
    private static final Set<UUID> gougeNoGravity = new HashSet<>();
    private static final Set<UUID> activeUse = new HashSet<>();

    private GougePhysics() {}

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem;
    }

    public static void cleanup(UUID id) {
        hangData.remove(id);
        trails.remove(id);
        gougeNoGravity.remove(id);
        activeUse.remove(id);
    }

    public static void markActive(UUID id) {
        activeUse.add(id);
    }

    public static boolean isActive(UUID id) {
        return activeUse.contains(id);
    }

    public static void checkStale(ServerPlayer player) {
        if (!gougeNoGravity.contains(player.getUUID())) return;
        if (!isPickaxe(player.getUseItem())) {
            player.setNoGravity(false);
            releaseHang(player.getUUID());
            clearCrack(player);
        }
    }

    public static void stopSliding(ServerPlayer player) {
        int[] d = hangData.get(player.getUUID());
        if (d != null) {
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
        }
        releaseHang(player.getUUID());
        clearCrack(player);
        player.setNoGravity(false);
        activeUse.remove(player.getUUID());
    }

    private static int[] data(UUID id) {
        return hangData.computeIfAbsent(id, k -> new int[]{-1, -1, -1, 0, 0});
    }

    public static void releaseHang(UUID id) {
        int[] d = hangData.get(id);
        if (d != null) d[0] = -1;
        gougeNoGravity.remove(id);
    }

    public static void clearActive(UUID id) {
        activeUse.remove(id);
    }

    public static void clearCrack(ServerPlayer player) {
        ArrayDeque<BlockPos> trail = trails.remove(player.getUUID());
        if (trail != null) {
            int slot = 0;
            for (BlockPos p : trail) sendCrack(player, p, -1, slot++);
        }
    }

    private static void sendCrack(ServerPlayer player, BlockPos pos, int stage, int slot) {
        int entityId = player.getId() + (slot + 1) * 10_000;
        player.connection.send(new ClientboundBlockDestructionPacket(entityId, pos, stage));
    }

    private static int enchLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var holder : enchants.keySet()) {
            if (holder.is(key)) return enchants.getLevel(holder);
        }
        return 0;
    }

    private static int hangDrain(Item item) {
        if (item == Items.WOODEN_PICKAXE)    return 1;
        if (item == Items.STONE_PICKAXE)     return 2;
        if (item == Items.GOLDEN_PICKAXE)    return 1;
        if (item == Items.IRON_PICKAXE)      return 3;
        if (item == Items.DIAMOND_PICKAXE)   return 5;
        if (item == Items.NETHERITE_PICKAXE) return 7;
        return 2;
    }

    private static int baseHangTicks(Item item) {
        if (item == Items.WOODEN_PICKAXE)    return 40;
        if (item == Items.STONE_PICKAXE)     return 80;
        if (item == Items.GOLDEN_PICKAXE)    return 30;
        if (item == Items.IRON_PICKAXE)      return 120;
        if (item == Items.DIAMOND_PICKAXE)   return 200;
        if (item == Items.NETHERITE_PICKAXE) return 300;
        return 60;
    }

    private static int maxHangTicks(ItemStack stack) {
        return baseHangTicks(stack.getItem()) + enchLevel(stack, GougeEnchantments.GRIP) * 40;
    }

    public static BlockHitResult raycast(Level world, Player user) {
        Vec3 start = user.getEyePosition(1.0f);
        Vec3 look = user.getViewVector(1.0f);
        BlockHitResult hit = world.clip(new ClipContext(
                start, start.add(look.scale(RAYCAST_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        if (hit.getType() == HitResult.Type.BLOCK) return hit;

        Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(RAYCAST_RANGE);
        BlockHitResult hHit = world.clip(new ClipContext(
                start, start.add(horiz),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return hHit.getType() == HitResult.Type.BLOCK ? hHit : null;
    }

    public static boolean applyImpactDamage(ItemStack stack, ServerLevel world, ServerPlayer player,
                                            double downwardSpeed, float hardness) {
        int damage = Math.round((float) (downwardSpeed * Math.min(hardness, 5.0f) * 3));
        stack.hurtAndBreak(damage, player, EquipmentSlot.MAINHAND);
        return !stack.isEmpty();
    }

    public static void spawnImpact(ServerLevel world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3 p = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundType();
        if (hardness >= HARD_MATERIAL_HARDNESS) {
            world.playSound(null, pos, sounds.getBreakSound(), SoundSource.PLAYERS, 2.0f, 0.5f);
            world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 20, 0.2, 0.2, 0.2, 0.15);
        } else {
            world.playSound(null, pos, sounds.getBreakSound(), SoundSource.PLAYERS, 1.5f, 0.6f);
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 25, 0.3, 0.3, 0.3, 0.0);
        }
    }

    public static boolean tick(ServerPlayer player) {
        int[] d = data(player.getUUID());
        boolean result = tick0(player, d);
        if (!result) {
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
        }
        return result;
    }

    public static float fallDamageMultiplier(UUID id) {
        int[] d = hangData.get(id);
        return (d != null && d[4] == 1) ? SOFT_LANDING_DAMAGE_RETENTION : 1.0f;
    }

    private static boolean tick0(ServerPlayer player, int[] d) {
        if (player.onGround()) {
            player.setNoGravity(false);
            releaseHang(player.getUUID());
            clearCrack(player);
            return false;
        }

        ServerLevel world = player.serverLevel();
        BlockHitResult hit = raycast(world, player);
        if (hit == null) {
            player.setNoGravity(false);
            releaseHang(player.getUUID());
            clearCrack(player);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        float hardness = Math.max(0f, state.getDestroySpeed(world, pos));
        boolean sneakingNow = player.isCrouching();
        boolean wasSneaking = d[3] == 1;
        d[3] = sneakingNow ? 1 : 0;
        boolean onCooldown = d[1] != -1 && player.tickCount < d[1];

        if (hardness >= HARD_MATERIAL_HARDNESS && !onCooldown) {
            if (wasSneaking && !sneakingNow) {
                d[2] = player.tickCount;
            } else if (!wasSneaking && sneakingNow) {
                if (d[2] != -1 && player.tickCount - d[2] <= DOUBLE_TAP_WINDOW_TICKS) {
                    d[2] = -1;
                    return wallKick(player, world, hit, d);
                }
            }

            d[4] = 0;
            player.setNoGravity(true);
            gougeNoGravity.add(player.getUUID());
            Vec3 v = player.getDeltaMovement();

            if (Math.abs(v.y) < HANG_LOCK_SPEED) {
                player.fallDistance = 0.0F;
                if (d[0] == -1) {
                    d[0] = player.tickCount + maxHangTicks(player.getUseItem());
                }

                if (player.tickCount >= d[0]) {
                    startCooldown(player, d);
                    spawnSlip(world, hit.getLocation());
                    clearCrack(player);
                    releaseHang(player.getUUID());
                    player.setNoGravity(false);
                    return false;
                }

                player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), Vec3.ZERO));

                if (player.tickCount % 20 == 0) {
                    ItemStack stack = player.getUseItem();
                    stack.hurtAndBreak(hangDrain(stack.getItem()), player, EquipmentSlot.MAINHAND);
                    if (stack.isEmpty()) {
                        releaseHang(player.getUUID());
                        player.setNoGravity(false);
                        return false;
                    }
                }
                if (player.tickCount % 20 == 10) {
                    world.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS,
                            0.4f, 0.8f + world.getRandom().nextFloat() * 0.2f);
                }
                return true;
            } else {
                d[0] = -1;
                double hardFriction = Mth.clamp(
                        HARD_FRICTION_BASE - hardness * HARD_FRICTION_SCALE, HARD_MIN_FRICTION, HARD_MAX_FRICTION);
                Vec3 slowed = new Vec3(v.x * HORIZONTAL_DAMPING, v.y * hardFriction, v.z * HORIZONTAL_DAMPING);
                player.setDeltaMovement(slowed);
                player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), slowed));
                updateClimbFx(player, world, hit, pos, state, hardness);
                return true;
            }
        }

        releaseHang(player.getUUID());
        player.setNoGravity(false);
        d[4] = 1;
        Vec3 v = player.getDeltaMovement();
        double speed = Math.abs(v.y);

        if (speed > 1.5) {
            List<Player> nearby = world.getEntitiesOfClass(Player.class,
                    player.getBoundingBox().inflate(0.8), p -> p != player);
            for (Player target : nearby) {
                target.hurt(world.damageSources().playerAttack(player), (float) (speed * 2));
            }
        }

        double slideSpeed = Mth.clamp(
                SOFT_SLIDE_MAX_SPEED - hardness * SOFT_SLIDE_HARDNESS_SCALE, SOFT_SLIDE_MIN_SPEED, SOFT_SLIDE_MAX_SPEED);
        double slideVy = v.y < 0 ? -slideSpeed : v.y;
        Vec3 slid = new Vec3(v.x * HORIZONTAL_DAMPING, slideVy, v.z * HORIZONTAL_DAMPING);
        player.setDeltaMovement(slid);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), slid));

        updateClimbFx(player, world, hit, pos, state, hardness);
        return true;
    }

    private static void updateClimbFx(ServerPlayer player, ServerLevel world, BlockHitResult hit,
                                       BlockPos pos, BlockState state, float hardness) {
        ArrayDeque<BlockPos> trail = trails.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        if (trail.isEmpty() || !trail.peekFirst().equals(pos)) {
            trail.addFirst(pos);
            if (trail.size() > TRAIL_LENGTH) {
                sendCrack(player, trail.removeLast(), -1, TRAIL_LENGTH);
            }
            int stage = 5;
            int slot = 0;
            for (BlockPos trailPos : trail) {
                sendCrack(player, trailPos, stage, slot++);
                if (stage > 1) stage--;
            }
        }

        if (player.tickCount % 5 == 0) {
            spawnJuice(world, hit, state, hardness);
        }
    }

    private static boolean wallKick(ServerPlayer player, ServerLevel world, BlockHitResult hit, int[] d) {
        startCooldown(player, d);
        d[0] = -1;
        releaseHang(player.getUUID());
        clearCrack(player);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;

        var dir = hit.getDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        Vec3 push = normal.scale(WALLKICK_HORIZONTAL).add(0, WALLKICK_VERTICAL, 0);
        player.setDeltaMovement(push);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), push));

        world.playSound(null, hit.getBlockPos(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        world.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                15, 0.2, 0.2, 0.2, 0.25);
        return true;
    }

    private static void startCooldown(ServerPlayer player, int[] d) {
        ItemStack stack = player.getUseItem();
        int momentum = enchLevel(stack, GougeEnchantments.MOMENTUM);
        int cooldown = Math.max(20, HANG_COOLDOWN_TICKS - momentum * 20);
        d[1] = player.tickCount + cooldown;
        player.getCooldowns().addCooldown(stack.getItem(), cooldown);
    }

    private static void spawnSlip(ServerLevel world, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        world.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.2);
    }

    private static void spawnJuice(ServerLevel world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3 p = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundType();
        if (hardness >= HARD_MATERIAL_HARDNESS) {
            world.playSound(null, pos, sounds.getHitSound(), SoundSource.PLAYERS,
                    0.6f, 0.6f + world.getRandom().nextFloat() * 0.2f);
            world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.05);
            double ox = hit.getDirection().getStepX() * 0.15;
            double oz = hit.getDirection().getStepZ() * 0.15;
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x + ox, p.y + 0.3, p.z + oz, 8, 0.05, 0.3, 0.05, 0.08);
        } else {
            world.playSound(null, pos, sounds.getHitSound(), SoundSource.PLAYERS, 0.5f, 1.0f);
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.0);
        }
    }
}
