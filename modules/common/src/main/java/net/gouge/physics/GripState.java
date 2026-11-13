package net.gouge.physics;

import net.gouge.config.GougeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.gouge.physics.GougePhysics.*;
import static net.gouge.physics.GripEffects.*;

public final class GripState {

    private GripState() {
    }

    static final Map<UUID, int[]> hangData = new ConcurrentHashMap<>();
    static final Map<UUID, Vec3> anchors = new ConcurrentHashMap<>();
    static final Map<UUID, ClimbPin> climbPins = new ConcurrentHashMap<>();
    static final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    static final Map<UUID, Double> slideVy = new ConcurrentHashMap<>();
    static final Set<UUID> gougeNoGravity = ConcurrentHashMap.newKeySet();
    static final Set<UUID> activeUse = ConcurrentHashMap.newKeySet();
    static final Map<UUID, Integer> mantled = new ConcurrentHashMap<>();
    static final Map<UUID, InteractionHand> activeHand = new ConcurrentHashMap<>();
    static final Map<UUID, ItemStack> activeStack = new ConcurrentHashMap<>();
    static final Map<UUID, Integer> launchCharge = new ConcurrentHashMap<>();
    static final Map<UUID, int[]> launchAssist = new ConcurrentHashMap<>();
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

    record ClimbPin(int grabTick, BlockPos pos, Direction face) {
    }

    public static void cleanup(UUID id) {
        slideVy.remove(id);
        activeHand.remove(id);
        activeStack.remove(id);
        mantled.remove(id);
        hangData.remove(id);
        trails.remove(id);
        anchors.remove(id);
        climbPins.remove(id);
        lastY.remove(id);
        gougeNoGravity.remove(id);
        activeUse.remove(id);
        launchCharge.remove(id);
        launchAssist.remove(id);
    }

    public static void markActive(UUID id, InteractionHand hand, ItemStack stack) {
        activeUse.add(id);
        activeHand.put(id, hand);
        activeStack.put(id, stack);
    }

    public static void setAnchor(UUID id, Vec3 pos) {
        anchors.put(id, pos);
    }

    public static boolean isActive(UUID id) {
        return activeUse.contains(id);
    }

    static int[] data(UUID id) {
        return hangData.computeIfAbsent(id, k -> new int[]{-1, -1, -1, 0, 0, -1, 0, 0});
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

    static double actualVerticalSpeed(ServerPlayer player) {
        UUID id = player.getUUID();
        double y = player.getY();
        Double prev = lastY.put(id, y);
        if (prev == null) {
            Vec3 anchor = anchors.get(id);
            prev = anchor != null ? anchor.y : y;
        }
        return y - prev;
    }

    public static float fallDamageMultiplier(UUID id) {
        int[] d = hangData.get(id);
        return (d != null && d[4] == 1) ? (float) GougeConfig.INSTANCE.mechanics.soft_fall_damage : 1.0f;
    }
}
