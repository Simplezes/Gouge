package net.gouge.paper;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class Geometry {

    private Geometry() {
    }

    static double riseOf(double boost) {
        double y = 0, vy = boost;
        for (int i = 0; i < 400 && vy > 0; i++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
        }
        return y;
    }

    static double boostFor(double rise) {
        double boost = 0.1;
        while (boost < 2.0 && riseOf(boost) < rise) boost += 0.01;
        return boost;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static boolean collides(World w, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    for (BoundingBox c : b.getCollisionShape().getBoundingBoxes()) {
                        if (c.shift(x, y, z).overlaps(box)) return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean hasClearance(Player p, double min) {
        return min <= 0 || p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), min,
                FluidCollisionMode.NEVER, true) == null;
    }

    static boolean solid(Block b) {
        return !b.getCollisionShape().getBoundingBoxes().isEmpty();
    }

    static boolean canStand(Block b) {
        return solid(b) && !solid(b.getRelative(BlockFace.UP)) && !solid(b.getRelative(BlockFace.UP, 2));
    }

    static double ledgeTop(Block b) {
        double max = 1.0;
        var boxes = b.getCollisionShape().getBoundingBoxes();
        if (!boxes.isEmpty()) {
            max = 0;
            for (BoundingBox bb : boxes) max = Math.max(max, bb.getMaxY());
        }
        return b.getY() + max;
    }

    static Vector center(Block b) {
        return new Vector(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    }

    static Vector faceVec(BlockFace f) {
        return new Vector(f.getModX(), f.getModY(), f.getModZ());
    }

    static double wallDistance(Block pin, BlockFace face, Location at) {
        boolean x = face.getModX() != 0;
        int step = x ? face.getModX() : face.getModZ();
        double plane = (x ? pin.getX() : pin.getZ()) + 0.5 + step * 0.5;
        return ((x ? at.getX() : at.getZ()) - plane) * step;
    }

    static Block climbLedge(World w, int px, int pz, double feetY) {
        int y = (int) Math.floor(feetY + 0.01);
        Block cell = w.getBlockAt(px, y, pz);
        if (!solid(cell)) {
            cell = cell.getRelative(BlockFace.DOWN);
            if (!solid(cell)) {
                cell = null;
                for (int up = y + 1; up <= y + 3; up++) {
                    Block b = w.getBlockAt(px, up, pz);
                    if (solid(b)) {
                        cell = b;
                        break;
                    }
                }
                if (cell == null) return null;
            }
        }
        for (int i = 0; i < 4 && solid(cell.getRelative(BlockFace.UP)); i++) cell = cell.getRelative(BlockFace.UP);
        return cell;
    }

    static boolean climbCapped(World w, Block ledge, Location at) {
        if (!solid(ledge.getRelative(BlockFace.UP)) && solid(ledge.getRelative(BlockFace.UP, 2))) return true;
        for (int y = (int) Math.floor(at.getY() + 1.81); y <= ledge.getY() + 2; y++) {
            if (solid(w.getBlockAt(at.getBlockX(), y, at.getBlockZ()))) return true;
        }
        return false;
    }

    static Hit resolve(Player p, RayTraceResult r) {
        if (r == null || r.getHitBlock() == null || r.getHitBlockFace() == null) return null;
        Block b = r.getHitBlock();
        BlockFace f = r.getHitBlockFace();
        Vector at = r.getHitPosition();
        if (f.getModY() == 0) return new Hit(b, f, at);
        if (f != BlockFace.UP) return null;
        double dx = b.getX() + 0.5 - p.getLocation().getX(), dz = b.getZ() + 0.5 - p.getLocation().getZ();
        if (dx * dx + dz * dz <= 0.05) return null;
        BlockFace wall = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? BlockFace.WEST : BlockFace.EAST)
                : (dz > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
        return new Hit(b, wall, at);
    }
}
