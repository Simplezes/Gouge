package net.gouge.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import static net.gouge.paper.Effects.*;
import static net.gouge.paper.Geometry.*;

final class Shimmy {

    private final GougePlugin plugin;

    Shimmy(GougePlugin plugin) {
        this.plugin = plugin;
    }

    int shimmyDir(Player p, Session s) {
        var cfg = plugin.getConfig();
        if (s.pin == null || s.pinFace == null || s.hangEnd == -1 || !cfg.getBoolean("traverse.enabled")
                || (cfg.getBoolean("traverse.requires_dual") && !plugin.rules.dualActive(p))) return 0;
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double lx = -Math.sin(yaw), lz = Math.cos(yaw);
        double wx = -s.pinFace.getModX(), wz = -s.pinFace.getModZ();
        double angle = Math.toDegrees(Math.atan2(wx * lz - wz * lx, wx * lx + wz * lz));
        double min = cfg.getDouble("traverse.look_yaw");
        return angle < -min ? 1 : angle > min ? -1 : 0;
    }

    Vector shimmyStep(Player p, Session s, ItemStack tool, int dir, Hit hit, Block hitBlock, float hardness) {
        var cfg = plugin.getConfig();
        BlockFace f = s.pinFace;
        double sp = cfg.getDouble("traverse.speed")
                * (1.0 + plugin.rules.enchLevel(tool, "shimmy") * cfg.getDouble("enchantments.traverse_bonus"));
        Vector d = new Vector(-f.getModZ() * dir * sp, 0, f.getModX() * dir * sp);
        Location loc = p.getLocation();
        World w = p.getWorld();
        int x = f.getModX() != 0 ? s.pin.getX() : (int) Math.floor(loc.getX() + d.getX() * 4);
        int z = f.getModX() != 0 ? (int) Math.floor(loc.getZ() + d.getZ() * 4) : s.pin.getZ();
        Block body = w.getBlockAt(x, (int) Math.floor(loc.getY() + 1.0), z);
        if (!solid(body) || !plugin.rules.isHard(body) || body.getType().getHardness() < 0 || collides(w, p.getBoundingBox().shift(d))) {
            return new Vector();
        }
        if (!body.equals(s.pin)) {
            int slot = 0;
            for (Block b : s.trail) plugin.effects.crack(p, b, slot++, 0f);
            s.trail.clear();
        }
        s.pin = body;
        s.anchor.add(d);
        plugin.effects.trail(p, s, body);
        if (plugin.now % 3 == 0) plugin.effects.chips(wallContact(p, f), body, 5, 0.25, 0.04);
        if (plugin.now % 5 == 0) plugin.effects.snd(loc, body.getBlockData().getSoundGroup().getHitSound(), 0.7f, 0.75f + (float) Math.random() * 0.3f);
        int cost = cfg.getInt("traverse.durability_cost");
        if (cost > 0 && plugin.now % 20 == 0) plugin.rules.damage(p, plugin.rules.slotOf(p), cost);
        return d;
    }
}
