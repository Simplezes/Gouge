package net.gouge;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GougePhysics {
    private static final double RAYCAST_RANGE = 2.5;
    private static final double SOFT_SLIDE_MAX_SPEED = 0.5;
    private static final double SOFT_SLIDE_MIN_SPEED = 0.3;
    private static final double SOFT_SLIDE_HARDNESS_SCALE = 0.13;
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
        return stack.isIn(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem;
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

    public static void checkStale(ServerPlayerEntity player) {
        if (!gougeNoGravity.contains(player.getUuid())) return;
        if (!isPickaxe(player.getActiveItem())) {
            player.setNoGravity(false);
            releaseHang(player.getUuid());
            clearCrack(player);
        }
    }

    public static void stopSliding(ServerPlayerEntity player) {
        int[] d = hangData.get(player.getUuid());
        if (d != null) {
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
        }
        releaseHang(player.getUuid());
        clearCrack(player);
        player.setNoGravity(false);
        activeUse.remove(player.getUuid());
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

    public static void clearCrack(ServerPlayerEntity player) {
        ArrayDeque<BlockPos> trail = trails.remove(player.getUuid());
        if (trail != null) {
            int slot = 0;
            for (BlockPos p : trail) sendCrack(player, p, -1, slot++);
        }
    }

    private static void sendCrack(ServerPlayerEntity player, BlockPos pos, int stage, int slot) {
        int entityId = player.getId() + (slot + 1) * 10_000;
        player.networkHandler.sendPacket(new BlockBreakingProgressS2CPacket(entityId, pos, stage));
    }

    private static int enchLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (var entry : enchants.getEnchantments()) {
            if (entry.matchesKey(key)) return enchants.getLevel(entry);
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

    public static BlockHitResult raycast(World world, PlayerEntity user) {
        Vec3d start = user.getCameraPosVec(1.0f);
        Vec3d look = user.getRotationVec(1.0f);
        BlockHitResult hit = world.raycast(new RaycastContext(
                start, start.add(look.multiply(RAYCAST_RANGE)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (hit.getType() == HitResult.Type.BLOCK) return hit;

        Vec3d horiz = new Vec3d(look.x, 0, look.z).normalize().multiply(RAYCAST_RANGE);
        BlockHitResult hHit = world.raycast(new RaycastContext(
                start, start.add(horiz),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        return hHit.getType() == HitResult.Type.BLOCK ? hHit : null;
    }

    public static boolean applyImpactDamage(ItemStack stack, ServerWorld world, ServerPlayerEntity player,
                                            double downwardSpeed, float hardness) {
        int damage = Math.round((float) (downwardSpeed * Math.min(hardness, 5.0f) * 3));
        stack.damage(damage, world, player, item -> {});
        return !stack.isEmpty();
    }

    public static void spawnImpact(ServerWorld world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3d p = hit.getPos();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundGroup();
        if (hardness >= HARD_MATERIAL_HARDNESS) {
            world.playSound(null, pos, sounds.getBreakSound(), SoundCategory.PLAYERS, 2.0f, 0.5f);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 20, 0.2, 0.2, 0.2, 0.15);
        } else {
            world.playSound(null, pos, sounds.getBreakSound(), SoundCategory.PLAYERS, 1.5f, 0.6f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 25, 0.3, 0.3, 0.3, 0.0);
        }
    }

    public static boolean tick(ServerPlayerEntity player) {
        int[] d = data(player.getUuid());
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

    private static boolean tick0(ServerPlayerEntity player, int[] d) {
        if (player.isOnGround()) {
            player.setNoGravity(false);
            releaseHang(player.getUuid());
            clearCrack(player);
            return false;
        }

        ServerWorld world = player.getServerWorld();
        BlockHitResult hit = raycast(world, player);
        if (hit == null) {
            player.setNoGravity(false);
            releaseHang(player.getUuid());
            clearCrack(player);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        float hardness = Math.max(0f, state.getHardness(world, pos));
        boolean sneakingNow = player.isSneaking();
        boolean wasSneaking = d[3] == 1;
        d[3] = sneakingNow ? 1 : 0;
        boolean onCooldown = d[1] != -1 && player.age < d[1];

        if (hardness >= HARD_MATERIAL_HARDNESS && !onCooldown) {
            if (wasSneaking && !sneakingNow) {
                d[2] = player.age;
            } else if (!wasSneaking && sneakingNow) {
                if (d[2] != -1 && player.age - d[2] <= DOUBLE_TAP_WINDOW_TICKS) {
                    d[2] = -1;
                    return wallKick(player, world, hit, d);
                }
            }

            d[4] = 0;
            player.setNoGravity(true);
            gougeNoGravity.add(player.getUuid());
            Vec3d v = player.getVelocity();

            if (Math.abs(v.y) < HANG_LOCK_SPEED) {
                player.fallDistance = 0.0F;
                if (d[0] == -1) {
                    d[0] = player.age + maxHangTicks(player.getActiveItem());
                }

                if (player.age >= d[0]) {
                    startCooldown(player, d);
                    spawnSlip(world, hit.getPos());
                    clearCrack(player);
                    releaseHang(player.getUuid());
                    player.setNoGravity(false);
                    return false;
                }

                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;

                if (player.age % 20 == 0) {
                    ItemStack stack = player.getActiveItem();
                    stack.damage(hangDrain(stack.getItem()), world, player, item -> {});
                    if (stack.isEmpty()) {
                        releaseHang(player.getUuid());
                        player.setNoGravity(false);
                        return false;
                    }
                }
                if (player.age % 20 == 10) {
                    world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS,
                            0.4f, 0.8f + world.random.nextFloat() * 0.2f);
                }
                return true;
            } else {
                d[0] = -1;
                double hardFriction = MathHelper.clamp(
                        HARD_FRICTION_BASE - hardness * HARD_FRICTION_SCALE, HARD_MIN_FRICTION, HARD_MAX_FRICTION);
                player.setVelocity(v.x * HORIZONTAL_DAMPING, v.y * hardFriction, v.z * HORIZONTAL_DAMPING);
                player.velocityModified = true;
                updateClimbFx(player, world, hit, pos, state, hardness);
                return true;
            }
        }

        d[0] = -1;
        gougeNoGravity.add(player.getUuid());
        player.setNoGravity(true);
        d[4] = 1;
        Vec3d v = player.getVelocity();
        double speed = Math.abs(v.y);

        if (speed > 1.5) {
            List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class,
                    player.getBoundingBox().expand(0.8), p -> p != player);
            for (PlayerEntity target : nearby) {
                target.damage(world.getDamageSources().playerAttack(player), (float) (speed * 2));
            }
        }

        double slideSpeed = MathHelper.clamp(
                SOFT_SLIDE_MAX_SPEED - hardness * SOFT_SLIDE_HARDNESS_SCALE, SOFT_SLIDE_MIN_SPEED, SOFT_SLIDE_MAX_SPEED);
        double slideVy = v.y < 0 ? -slideSpeed : v.y;
        player.setVelocity(v.x * HORIZONTAL_DAMPING, slideVy, v.z * HORIZONTAL_DAMPING);
        player.velocityModified = true;

        updateClimbFx(player, world, hit, pos, state, hardness);
        return true;
    }

    private static void updateClimbFx(ServerPlayerEntity player, ServerWorld world, BlockHitResult hit,
                                       BlockPos pos, BlockState state, float hardness) {
        ArrayDeque<BlockPos> trail = trails.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
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

        if (player.age % 5 == 0) {
            spawnJuice(world, hit, state, hardness);
        }
    }

    private static boolean wallKick(ServerPlayerEntity player, ServerWorld world, BlockHitResult hit, int[] d) {
        startCooldown(player, d);
        d[0] = -1;
        releaseHang(player.getUuid());
        clearCrack(player);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;

        Vec3d normal = Vec3d.of(hit.getSide().getVector());
        player.setVelocity(normal.multiply(WALLKICK_HORIZONTAL).add(0, WALLKICK_VERTICAL, 0));
        player.velocityModified = true;

        world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.spawnParticles(ParticleTypes.CRIT, hit.getPos().x, hit.getPos().y, hit.getPos().z,
                15, 0.2, 0.2, 0.2, 0.25);
        return true;
    }

    private static void startCooldown(ServerPlayerEntity player, int[] d) {
        ItemStack stack = player.getActiveItem();
        int momentum = enchLevel(stack, GougeEnchantments.MOMENTUM);
        int cooldown = Math.max(20, HANG_COOLDOWN_TICKS - momentum * 20);
        d[1] = player.age + cooldown;
        player.getItemCooldownManager().set(stack.getItem(), cooldown);
    }

    private static void spawnSlip(ServerWorld world, Vec3d p) {
        BlockPos pos = BlockPos.ofFloored(p);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.2);
    }

    private static void spawnJuice(ServerWorld world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3d p = hit.getPos();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundGroup();
        if (hardness >= HARD_MATERIAL_HARDNESS) {
            world.playSound(null, pos, sounds.getHitSound(), SoundCategory.PLAYERS,
                    0.6f, 0.6f + world.random.nextFloat() * 0.2f);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.05);
            double ox = hit.getSide().getOffsetX() * 0.15;
            double oz = hit.getSide().getOffsetZ() * 0.15;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    p.x + ox, p.y + 0.3, p.z + oz, 8, 0.05, 0.3, 0.05, 0.08);
        } else {
            world.playSound(null, pos, sounds.getHitSound(), SoundCategory.PLAYERS, 0.5f, 1.0f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.0);
        }
    }
}
