package net.gouge.physics;

import net.gouge.config.GougeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static net.gouge.physics.BlockRules.*;

public final class WallGeometry {

    private WallGeometry() {
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
            if (world.getBlockState(up).getCollisionShape(world, up).isEmpty()
                    || !isHardBlock(world.getBlockState(up))) {
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

    public static boolean softAhead(Level world, BlockPos pin, double feetY, double step) {
        if (!GougeConfig.INSTANCE.climbing.stop_at_soft_block) {
            return false;
        }
        BlockPos hands = new BlockPos(pin.getX(), Mth.floor(feetY + step + 1.0), pin.getZ());
        BlockState at = world.getBlockState(hands);
        return !at.getCollisionShape(world, hands).isEmpty() && !isHardBlock(at);
    }

    public static BlockPos climbFocus(Level world, BlockPos pin, double feetY) {
        BlockPos chest = new BlockPos(pin.getX(), Mth.floor(feetY + 1.0), pin.getZ());
        BlockState at = world.getBlockState(chest);
        return !at.isAir() && isHardBlock(at) ? chest : pin;
    }

    public static BlockHitResult pinnedHit(Level world, BlockPos pin, Direction face) {
        if (world.getBlockState(pin).isAir()) {
            return null;
        }
        Vec3 at = Vec3.atCenterOf(pin).add(face.getStepX() * 0.5, 0, face.getStepZ() * 0.5);
        return new BlockHitResult(at, face, pin, false);
    }

    public static BlockPos handsCell(BlockPos pin, double feetY, double step) {
        return new BlockPos(pin.getX(), Mth.floor(feetY + step + 1.0), pin.getZ());
    }

    public static BlockPos launchCap(Level world, BlockPos pin, double feetY, double step) {
        return launchCap(world, pin, feetY, step, 0);
    }

    public static BlockPos launchCap(Level world, BlockPos pin, double feetY, double step, int extraHeight) {
        BlockPos hands = handsCell(pin, feetY, step);
        for (int i = 0; i < GougeConfig.INSTANCE.launch.max_cap_height + extraHeight; i++) {
            BlockPos cell = hands.above(i);
            BlockState st = world.getBlockState(cell);
            if (st.getCollisionShape(world, cell).isEmpty()) {
                return null;
            }
            if (canStandOnLedge(world, cell, st)) {
                return cell;
            }
            if (isHardBlock(st)) {
                return null;
            }
        }
        return null;
    }

    public static double launchRise(Level world, BlockPos cap, double feetY) {
        return ledgeTopY(world, cap, world.getBlockState(cap)) - feetY + 0.4;
    }

    public static BlockPos traverseCell(Level world, BlockPos pin, Direction face, Vec3 dest, double feetY) {
        BlockPos column = tangentCell(pin, face, dest);
        BlockPos body = new BlockPos(column.getX(), Mth.floor(feetY + 1.0), column.getZ());
        BlockState st = world.getBlockState(body);
        return !st.getCollisionShape(world, body).isEmpty() && isHardBlock(st) && st.getDestroySpeed(world, body) >= 0
                ? body : null;
    }
}
