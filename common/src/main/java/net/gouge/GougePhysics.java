package net.gouge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
    private static final Map<UUID, ClimbPin> climbPins = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> slideVy = new ConcurrentHashMap<>();
    private static final Set<UUID> gougeNoGravity = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> activeUse = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> mantled = new ConcurrentHashMap<>();
    private static final Map<UUID, InteractionHand> activeHand = new ConcurrentHashMap<>();

    private static volatile List<Map.Entry<TagKey<Item>, GougeConfig.PickaxeStats>> tagStatsCache;

    private GougePhysics() {
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || GougePlatform.get().isPickaxeTool(stack);
    }

    public static boolean hasDualPickaxes(Player player) {
        return isPickaxe(player.getMainHandItem()) && isPickaxe(player.getOffhandItem());
    }

    public static boolean isDualGripActive(Player player) {
        return GougeConfig.INSTANCE.mechanics.dual_grip_enabled && hasDualPickaxes(player);
    }

    public static double ledgeTopY(Level world, BlockPos pos, BlockState state) {
        var shape = state.getCollisionShape(world, pos);
        return pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
    }

    public static boolean canStandOnLedge(Level world, BlockPos pos, BlockState state) {
        BlockPos above1 = pos.above();
        BlockPos above2 = pos.above(2);
        return !state.getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(above1).getCollisionShape(world, above1).isEmpty()
                && world.getBlockState(above2).getCollisionShape(world, above2).isEmpty();
    }

    public static Vec3 mantleVelocity(Player player, Direction wallDir, BlockPos ledge) {
        double dx = ledge.getX() + 0.5 - player.getX();
        double dz = ledge.getZ() + 0.5 - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double fx = len > 1.0E-4 ? dx / len : -wallDir.getStepX();
        double fz = len > 1.0E-4 ? dz / len : -wallDir.getStepZ();
        double speed = Mth.clamp(len / 6.0, 0.12, 0.3);
        return new Vec3(fx * speed, 0.47, fz * speed);
    }

    public static boolean isJumpHeld(ServerPlayer player) {
        return player.getLastClientInput().jump() || player.isJumping();
    }

    public static float strafeIntent(ServerPlayer player) {
        var in = player.getLastClientInput();
        return in.left() == in.right() ? 0.0F : (in.left() ? 1.0F : -1.0F);
    }

    public static final int MANTLE_REGRAB_TICKS = 20;

    public static boolean hasMantled(ServerPlayer player) {
        Integer at = mantled.get(player.getUUID());
        if (at == null) {
            return false;
        }
        if (serverTick(player) - at >= MANTLE_REGRAB_TICKS) {
            mantled.remove(player.getUUID());
            return false;
        }
        return true;
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

    private record ClimbPin(int grabTick, BlockPos pos, Direction face) {
    }

    public static BlockPos tangentCell(BlockPos pin, Direction face, Vec3 at) {
        return face.getAxis() == Direction.Axis.X
                ? new BlockPos(pin.getX(), pin.getY(), Mth.floor(at.z))
                : new BlockPos(Mth.floor(at.x), pin.getY(), pin.getZ());
    }

    public static BlockPos trackWall(Level world, BlockPos pin, Direction face, Vec3 at, boolean strafing) {
        BlockPos follow = tangentCell(pin, face, at);
        return !strafing || follow.equals(pin) || climbLedge(world, follow, at.y) == null ? pin : follow;
    }

    public static double wallDistance(BlockPos pin, Direction face, Vec3 at) {
        int step = face.getAxis() == Direction.Axis.X ? face.getStepX() : face.getStepZ();
        double plane = face.getAxis() == Direction.Axis.X ? pin.getX() : pin.getZ();
        if (step > 0) {
            plane += 1;
        }
        double coord = face.getAxis() == Direction.Axis.X ? at.x : at.z;
        return (coord - plane) * step;
    }

    public static BlockPos climbLedge(Level world, BlockPos pin, double feetY) {
        int y = Mth.floor(feetY + 0.01);
        BlockPos cell = new BlockPos(pin.getX(), y, pin.getZ());
        if (world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()) {
            cell = cell.below();
            if (world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()) {
                cell = null;
                for (int up = y + 1; up <= y + 3; up++) {
                    BlockPos above = new BlockPos(pin.getX(), up, pin.getZ());
                    if (!world.getBlockState(above).getCollisionShape(world, above).isEmpty()) {
                        cell = above;
                        break;
                    }
                }
                if (cell == null) {
                    return null;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            BlockPos up = cell.above();
            if (world.getBlockState(up).getCollisionShape(world, up).isEmpty()) {
                break;
            }
            cell = up;
        }
        return cell;
    }

    public static boolean climbCapped(Level world, BlockPos ledge, Vec3 at, double feetY) {
        BlockPos above1 = ledge.above();
        BlockPos above2 = ledge.above(2);
        if (world.getBlockState(above1).getCollisionShape(world, above1).isEmpty()
                && !world.getBlockState(above2).getCollisionShape(world, above2).isEmpty()) {
            return true;
        }
        for (int y = Mth.floor(feetY + 1.81); y <= ledge.getY() + 2; y++) {
            BlockPos cell = BlockPos.containing(at.x, y, at.z);
            if (!world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos climbFocus(Level world, BlockPos pin, double feetY) {
        BlockPos chest = new BlockPos(pin.getX(), Mth.floor(feetY + 1.0), pin.getZ());
        return world.getBlockState(chest).isAir() ? pin : chest;
    }

    public static BlockHitResult pinnedHit(Level world, BlockPos pin, Direction face) {
        if (world.getBlockState(pin).isAir()) {
            return null;
        }
        Vec3 at = Vec3.atCenterOf(pin).add(face.getStepX() * 0.5, 0, face.getStepZ() * 0.5);
        return new BlockHitResult(at, face, pin, false);
    }

    private enum ClimbResult {
        SUCCESS,
        BLOCKED,
        RELEASED
    }

    public static boolean isHardBlock(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String override = GougeConfig.INSTANCE.block_overrides.get(id.toString());
        if (override != null) {
            return override.equals("hard");
        }
        if (GougeConfig.INSTANCE.mechanics.cling_everything) {
            return true;
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
        slideVy.remove(id);
        activeHand.remove(id);
        mantled.remove(id);
        hangData.remove(id);
        trails.remove(id);
        anchors.remove(id);
        climbPins.remove(id);
        lastY.remove(id);
        gougeNoGravity.remove(id);
        activeUse.remove(id);
    }

    public static void markActive(UUID id, InteractionHand hand) {
        activeUse.add(id);
        activeHand.put(id, hand);
    }

    public static InteractionHand handOverHand(ServerPlayer player) {
        InteractionHand was = activeHand.get(player.getUUID());
        if (was == null || !activeUse.contains(player.getUUID()) || isPickaxe(player.getItemInHand(was))) {
            return null;
        }
        InteractionHand other = was == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return isPickaxe(player.getItemInHand(other)) ? other : null;
    }

    public static void setAnchor(UUID id, Vec3 pos) {
        anchors.put(id, pos);
    }

    public static boolean isActive(UUID id) {
        return activeUse.contains(id);
    }

    public static void checkStale(ServerPlayer player) {
        UUID id = player.getUUID();
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
                return;
            }
            stopSliding(player);
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

    private static int[] data(UUID id) {
        return hangData.computeIfAbsent(id, k -> new int[]{-1, -1, -1, 0, 0, -1, 0, 0});
    }

    public static double hangHoldSpeed() {
        GougeConfig.Climbing c = GougeConfig.INSTANCE.climbing;
        return Math.max(HANG_LOCK_SPEED * 2, c.enabled ? c.speed * 1.5 : 0);
    }

    public static void releaseHang(UUID id) {
        slideVy.remove(id);
        int[] d = hangData.get(id);
        if (d != null) {
            d[0] = -1;
            d[5] = -1;
            d[6] = 0;
            d[7] = 0;
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
        lastY.remove(player.getUUID());
    }

    private static double actualVerticalSpeed(ServerPlayer player) {
        UUID id = player.getUUID();
        double y = player.getY();
        Double prev = lastY.put(id, y);
        if (prev == null) {
            Vec3 anchor = anchors.get(id);
            prev = anchor != null ? anchor.y : y;
        }
        return y - prev;
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
        return player.level().getServer().getTickCount();
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
            Identifier tagId = Identifier.tryParse(entry.getKey().substring(1));
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
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    public static int singlePickaxeHangTicks(ItemStack stack) {
        GougeConfig.PickaxeStats stats = getStats(stack);
        int baseTicks = (int) (stats.hang_time * 20);
        int bonusTicks = (int) (GougeConfig.INSTANCE.enchantments.grip_bonus * 20);
        return baseTicks + enchLevel(stack, GougeEnchantments.GRIP) * bonusTicks;
    }

    public static int maxHangTicks(Player player, ItemStack stack) {
        int ticks = singlePickaxeHangTicks(stack);
        if (isDualGripActive(player)) {
            ticks = (int) Math.round(ticks * GougeConfig.INSTANCE.mechanics.dual_grip_multiplier);
        }
        return Math.max(1, ticks);
    }

    public static BlockHitResult resolveHit(BlockHitResult hit, Player user) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        var dir = hit.getDirection();
        if (dir.getAxis().isHorizontal()) {
            return hit;
        }
        if (dir == Direction.UP) {
            BlockPos pos = hit.getBlockPos();
            double dx = (pos.getX() + 0.5) - user.getX();
            double dz = (pos.getZ() + 0.5) - user.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > 0.05) {
                Direction wallDir = Math.abs(dx) > Math.abs(dz)
                        ? (dx > 0 ? Direction.WEST : Direction.EAST)
                        : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                return hit.withDirection(wallDir);
            }
        }
        return null;
    }

    public static BlockHitResult raycast(Level world, Player user) {
        double reach = GougeConfig.INSTANCE.mechanics.reach;
        Vec3 start = user.getEyePosition(1.0f);
        Vec3 look = user.getViewVector(1.0f);
        BlockHitResult hit = world.clip(new ClipContext(
                start, start.add(look.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        BlockHitResult resolved = resolveHit(hit, user);
        if (resolved != null) {
            return resolved;
        }

        Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(reach);
        if (horiz.lengthSqr() < 1.0E-6) {
            return null;
        }
        BlockHitResult hHit = world.clip(new ClipContext(
                start, start.add(horiz),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return resolveHit(hHit, user);
    }

    public static boolean hasFallClearance(Level world, Player player, double minDistance) {
        if (minDistance <= 0) {
            return true;
        }
        Vec3 feet = player.position();
        BlockHitResult hit = world.clip(new ClipContext(
                feet, feet.subtract(0, minDistance, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.BLOCK;
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

    public static float fallDamageMultiplier(UUID id) {
        int[] d = hangData.get(id);
        return (d != null && d[4] == 1) ? (float) GougeConfig.INSTANCE.mechanics.soft_fall_damage : 1.0f;
    }

    private static boolean tick0(ServerPlayer player, int[] d) {
        if (player.onGround() || player.isSpectator() || player.getAbilities().flying) {
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
        if (savedPin != null && savedPin.grabTick() == d[5] && climbAllowed && isJumpHeld(player)) {
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
                    d[7] = singlePickaxeHangTicks(useItem);
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

                if (!GougeConfig.INSTANCE.mechanics.unlimited_grip && now >= d[0]) {
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
            d[5] = -1;
            d[6] = 0;
            d[7] = 0;
            double hardFriction = Mth.clamp(
                    HARD_FRICTION_BASE - hardness * HARD_FRICTION_SCALE, HARD_MIN_FRICTION, HARD_MAX_FRICTION);
            double slideSpeedY = (slide != null ? slide : Math.min(realVy, 0)) * hardFriction;
            slideVy.put(uid, slideSpeedY);
            player.setDeltaMovement(new Vec3(v.x * HORIZONTAL_DAMPING, slideSpeedY, v.z * HORIZONTAL_DAMPING));
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
            updateClimbFx(player, world, hit, pos, state, hardness, now);
            return true;
        }

        d[0] = -1;
        d[5] = -1;
        d[6] = 0;
        d[7] = 0;
        slideVy.remove(uid);
        Vec3 v = player.getDeltaMovement();

        if (realVy > 0.01) {
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

    private static ClimbResult handleClimb(ServerPlayer player, ServerLevel world, BlockHitResult hit,
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
            GougePhysics.clearActive(id);
            world.playSound(null, ledge, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
            return ClimbResult.RELEASED;
        }

        if (climbCapped(world, ledge, player.position(), player.getY())) {
            return ClimbResult.BLOCKED;
        }

        double speed = GougeConfig.INSTANCE.climbing.speed;
        double dy = speed;
        double dx = 0.0;
        double dz = 0.0;

        if (GougeConfig.INSTANCE.climbing.allow_diagonal) {
            float leftIntent = strafeIntent(player);
            if (leftIntent != 0) {
                double tx = dir.getStepZ();
                double tz = -dir.getStepX();
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

        if (now % 10 == 0) {
            world.playSound(null, destBlockPos, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS,
                    0.4f, 1.2f + world.getRandom().nextFloat() * 0.2f);
            world.sendParticles(ParticleTypes.CRIT, contact.x, contact.y, contact.z,
                    2, 0.05, 0.05, 0.05, 0.05);
        }

        return ClimbResult.SUCCESS;
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
        d[5] = -1;
        d[6] = 0;
        d[7] = 0;
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
            player.getCooldowns().addCooldown(stack, cooldown);
        }
    }

    private static void spawnSlip(ServerLevel world, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        world.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
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
