package net.gouge.physics;

import net.gouge.config.GougeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WallRaycast {

    private WallRaycast() {
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
        BlockHitResult flat = resolveHit(hHit, user);
        if (flat != null) {
            return flat;
        }
        Vec3 low = user.position().add(0, 0.9, 0);
        return resolveHit(world.clip(new ClipContext(
                low, low.add(horiz),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)), user);
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
}
