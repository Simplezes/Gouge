package net.gouge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GougePhysics {

    static final double SOFT_SLIDE_MAX_SPEED = 0.5;
    static final double SOFT_SLIDE_MIN_SPEED = 0.3;
    static final double SOFT_SLIDE_HARDNESS_SCALE = 0.13;
    static final double HORIZONTAL_DAMPING = 0.5;
    static final double HARD_FRICTION_BASE = 0.90;
    static final double HARD_FRICTION_SCALE = 0.03;
    static final double HARD_MIN_FRICTION = 0.55;
    static final double HARD_MAX_FRICTION = 0.90;
    static final double HANG_LOCK_SPEED = 0.08;
    private static final int CRACK_SLOT_STRIDE = 32;

    private static final GougeConfig.PickaxeStats FALLBACK_STATS = new GougeConfig.PickaxeStats(3.0, 2);

    private static final Map<UUID, int[]> hangData = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrayDeque<BlockPos>> trails = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> anchors = new ConcurrentHashMap<>();
    private static final Set<UUID> gougeNoGravity = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> activeUse = ConcurrentHashMap.newKeySet();

    private static volatile List<Map.Entry<TagKey<Item>, GougeConfig.PickaxeStats>> tagStatsCache;

    private GougePhysics() {
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem
                || GougePlatform.get().isPickaxeTool(stack);
    }

    public static boolean isHardBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String override = GougeConfig.INSTANCE.block_overrides.get(id.toString());
        if (override != null) {
            return override.equals("hard");
        }
        if (!id.getNamespace().equals("minecraft")) {
            return true;
        }
        return state.requiresCorrectToolForDrops();
    }

    public static void invalidateCache() {
        tagStatsCache = null;
    }

    public static void cleanup(UUID id) {
        hangData.remove(id);
        trails.remove(id);
        anchors.remove(id);
        gougeNoGravity.remove(id);
        activeUse.remove(id);
    }

    public static void markActive(UUID id) {
        activeUse.add(id);
    }

    public static void setAnchor(UUID id, Vec3 pos) {
        anchors.put(id, pos);
    }

    public static boolean isActive(UUID id) {
        return activeUse.contains(id);
    }

    public static void checkStale(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!activeUse.contains(id)) {
            int[] d = hangData.get(id);
            if (d != null) {
                d[0] = -1;
                d[2] = -1;
                d[3] = 0;
                d[4] = 0;
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
            stopSliding(player);
        }
    }

    public static void stopSliding(ServerPlayer player) {
        int[] d = hangData.get(player.getUUID());
        if (d != null) {
            d[2] = -1;
            d[3] = 0;
            d[4] = 0;
        }
        release(player);
        activeUse.remove(player.getUUID());
    }

    private static int[] data(UUID id) {
        return hangData.computeIfAbsent(id, k -> new int[]{-1, -1, -1, 0, 0});
    }

    public static void releaseHang(UUID id) {
        int[] d = hangData.get(id);
        if (d != null) {
            d[0] = -1;
        }
        gougeNoGravity.remove(id);
    }

    public static void clearActive(UUID id) {
        activeUse.remove(id);
    }

    private static void release(ServerPlayer player) {
        player.setNoGravity(false);
        releaseHang(player.getUUID());
        clearCrack(player);
        anchors.remove(player.getUUID());
    }

    public static void clearCrack(ServerPlayer player) {
        ArrayDeque<BlockPos> trail = trails.remove(player.getUUID());
        if (trail != null) {
            int slot = 0;
            for (BlockPos p : trail) {
                sendCrack(player, p, -1, slot++);
            }
        }
    }

    private static void sendCrack(ServerPlayer player, BlockPos pos, int stage, int slot) {
        int entityId = -1 - (player.getId() * CRACK_SLOT_STRIDE + slot);
        player.connection.send(new ClientboundBlockDestructionPacket(entityId, pos, stage));
    }

    private static int serverTick(ServerPlayer player) {
        return player.server.getTickCount();
    }

    private static EquipmentSlot usedSlot(ServerPlayer player) {
        return player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }

    private static int enchLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var holder : enchants.keySet()) {
            if (holder.is(key)) {
                return enchants.getLevel(holder);
            }
        }
        return 0;
    }

    private static List<Map.Entry<TagKey<Item>, GougeConfig.PickaxeStats>> tagStats() {
        List<Map.Entry<TagKey<Item>, GougeConfig.PickaxeStats>> cache = tagStatsCache;
        if (cache != null) {
            return cache;
        }
        List<Map.Entry<TagKey<Item>, GougeConfig.PickaxeStats>> built = new ArrayList<>();
        for (var entry : GougeConfig.INSTANCE.pickaxes.entrySet()) {
            if (!entry.getKey().startsWith("#")) {
                continue;
            }
            ResourceLocation tagId = ResourceLocation.tryParse(entry.getKey().substring(1));
            if (tagId == null) {
                GougeConfig.LOGGER.warn("Ignoring invalid pickaxe tag key in gouge.toml: {}", entry.getKey());
                continue;
            }
            built.add(Map.entry(TagKey.create(Registries.ITEM, tagId), entry.getValue()));
        }
        tagStatsCache = built;
        return built;
    }

    private static GougeConfig.PickaxeStats getStats(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GougeConfig.PickaxeStats direct = GougeConfig.INSTANCE.pickaxes.get(id.toString());
        if (direct != null) {
            return direct;
        }
        for (var entry : tagStats()) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        GougeConfig.PickaxeStats fallback = GougeConfig.INSTANCE.pickaxes.get("fallback");
        return fallback != null ? fallback : FALLBACK_STATS;
    }

    private static int maxHangTicks(ItemStack stack) {
        GougeConfig.PickaxeStats stats = getStats(stack);
        int baseTicks = (int) (stats.hang_time * 20);
        int bonusTicks = (int) (GougeConfig.INSTANCE.enchantments.grip_bonus * 20);
        return baseTicks + enchLevel(stack, GougeEnchantments.GRIP) * bonusTicks;
    }

    public static BlockHitResult raycast(Level world, Player user) {
        double reach = GougeConfig.INSTANCE.mechanics.reach;
        Vec3 start = user.getEyePosition(1.0f);
        Vec3 look = user.getViewVector(1.0f);
        BlockHitResult hit = world.clip(new ClipContext(
                start, start.add(look.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }

        Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(reach);
        if (horiz.lengthSqr() < 1.0E-6) {
            return null;
        }
        BlockHitResult hHit = world.clip(new ClipContext(
                start, start.add(horiz),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return hHit.getType() == HitResult.Type.BLOCK ? hHit : null;
    }

    public static boolean applyImpactDamage(ItemStack stack, ServerLevel world, ServerPlayer player,
            double downwardSpeed, float hardness, EquipmentSlot slot) {
        int damage = Math.round((float) (downwardSpeed * Math.min(hardness, 5.0f) * 3));
        if (damage > 0) {
            stack.hurtAndBreak(damage, player, slot);
        }
        return !stack.isEmpty();
    }

    public static void spawnImpact(ServerLevel world, BlockHitResult hit, BlockState state, float hardness) {
        Vec3 p = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        var sounds = state.getSoundType();
        if (isHardBlock(state)) {
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
        return (d != null && d[4] == 1) ? (float) GougeConfig.INSTANCE.mechanics.soft_fall_damage : 1.0f;
    }

    private static boolean tick0(ServerPlayer player, int[] d) {
        if (player.onGround() || player.isSpectator() || player.getAbilities().flying) {
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

        ServerLevel world = player.serverLevel();
        BlockHitResult hit = raycast(world, player);
        if (hit == null) {
            release(player);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        float rawHardness = state.getDestroySpeed(world, pos);
        if (rawHardness < 0) {
            release(player);
            return false;
        }
        float hardness = Math.max(0f, rawHardness);

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

        if (isHardBlock(state)) {
            d[4] = 0;
            player.setNoGravity(true);
            gougeNoGravity.add(player.getUUID());
            Vec3 v = player.getDeltaMovement();

            if (Math.abs(v.y) < HANG_LOCK_SPEED) {
                player.fallDistance = 0.0F;
                if (d[0] == -1) {
                    d[0] = now + maxHangTicks(player.getUseItem());
                }

                if (now >= d[0]) {
                    startCooldown(player, d);
                    spawnSlip(world, hit.getLocation());
                    release(player);
                    return false;
                }

                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;

                if (now % 20 == 0 && !drain(player)) {
                    release(player);
                    return false;
                }
                if (now % 20 == 10) {
                    world.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS,
                            0.4f, 0.8f + world.getRandom().nextFloat() * 0.2f);
                }
                return true;
            }

            d[0] = -1;
            double hardFriction = Mth.clamp(
                    HARD_FRICTION_BASE - hardness * HARD_FRICTION_SCALE, HARD_MIN_FRICTION, HARD_MAX_FRICTION);
            player.setDeltaMovement(new Vec3(v.x * HORIZONTAL_DAMPING, v.y * hardFriction, v.z * HORIZONTAL_DAMPING));
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
            updateClimbFx(player, world, hit, pos, state, hardness, now);
            return true;
        }

        d[0] = -1;
        Vec3 v = player.getDeltaMovement();

        if (v.y >= 0) {
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
        player.setDeltaMovement(new Vec3(v.x * HORIZONTAL_DAMPING, -slideSpeed, v.z * HORIZONTAL_DAMPING));
        player.hurtMarked = true;

        updateClimbFx(player, world, hit, pos, state, hardness, now);
        return true;
    }

    private static boolean drain(ServerPlayer player) {
        ItemStack stack = player.getUseItem();
        GougeConfig.PickaxeStats stats = getStats(stack);
        if (stats.damage_rate > 0) {
            stack.hurtAndBreak(stats.damage_rate, player, usedSlot(player));
        }
        return !stack.isEmpty();
    }

    private static void updateClimbFx(ServerPlayer player, ServerLevel world, BlockHitResult hit,
            BlockPos pos, BlockState state, float hardness, int now) {
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

    private static boolean wallKick(ServerPlayer player, ServerLevel world, BlockHitResult hit, int[] d) {
        startCooldown(player, d);
        d[0] = -1;
        d[4] = 0;
        release(player);
        player.fallDistance = 0.0F;

        var dir = hit.getDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double kickH = GougeConfig.INSTANCE.wall_jump.forward_boost;
        double kickV = GougeConfig.INSTANCE.wall_jump.upward_boost;
        player.setDeltaMovement(normal.scale(kickH).add(0, kickV, 0));
        player.hurtMarked = true;

        world.playSound(null, hit.getBlockPos(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        world.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                15, 0.2, 0.2, 0.2, 0.25);
        return true;
    }

    private static void startCooldown(ServerPlayer player, int[] d) {
        ItemStack stack = player.getUseItem();
        int momentum = enchLevel(stack, GougeEnchantments.MOMENTUM);
        int baseCooldown = (int) (GougeConfig.INSTANCE.mechanics.slip_cooldown * 20);
        int reduction = (int) (GougeConfig.INSTANCE.enchantments.momentum_reduction * 20);
        int cooldown = Math.max(0, baseCooldown - momentum * reduction);
        d[1] = cooldown > 0 ? serverTick(player) + cooldown : -1;
        if (cooldown > 0 && !stack.isEmpty()) {
            player.getCooldowns().addCooldown(stack.getItem(), cooldown);
        }
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
        if (isHardBlock(state)) {
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
