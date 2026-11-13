package net.gouge.paper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

import static net.gouge.paper.Effects.*;
import static net.gouge.paper.Geometry.*;
import static net.gouge.paper.Tuning.*;

final class Launch {

    private final GougePlugin plugin;

    Launch(GougePlugin plugin) {
        this.plugin = plugin;
    }

    boolean tryLaunch(Player p, Session s, ItemStack tool) {
        var cfg = plugin.getConfig();
        World w = p.getWorld();
        Location loc = p.getLocation();
        Block hands = w.getBlockAt(s.pin.getX(), (int) Math.floor(loc.getY() + cfg.getDouble("climbing.speed") + 1.0), s.pin.getZ());
        Block cap = cfg.getBoolean("launch.enabled") && solid(hands) && !plugin.rules.isHard(hands) ? capTop(hands) : null;
        if (cap == null) {
            s.launchCharge = 0;
            return false;
        }
        if (++s.launchCharge < (int) (cfg.getDouble("launch.charge_time") * 20)) {
            if (s.launchCharge % 2 == 1) plugin.effects.chips(wallContact(p, s.pinFace), hands, 4, 0.2, 0.02);
            if (s.launchCharge % 4 == 1) plugin.effects.snd(loc, hands.getBlockData().getSoundGroup().getHitSound(), 0.8f, 0.55f);
            return false;
        }
        s.launchCharge = 0;
        p.setFallDistance(0f);
        double base = cfg.getDouble("launch.upward_boost");
        if (cfg.getBoolean("launch.auto_height")) {
            base = boostFor(ledgeTop(cap) - loc.getY() + 0.4) + 0.02;
        }
        p.setVelocity(new Vector(0, base * plugin.rules.spring(tool), 0));
        plugin.mantled.put(p.getUniqueId(), plugin.now);
        plugin.assistLeft.put(p.getUniqueId(), ASSIST_TICKS);
        plugin.assistFace.put(p.getUniqueId(), s.pinFace);
        Location at = loc.clone().add(0, 1, 0).subtract(s.pinFace.getModX() * 0.4, 0, s.pinFace.getModZ() * 0.4);
        plugin.effects.snd(loc, hands.getBlockData().getSoundGroup().getBreakSound(), 1.3f, 0.8f);
        plugin.effects.snd(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.4f);
        plugin.effects.chips(at, hands, 30, 0.3, 0.12);
        if (cfg.getBoolean("effects.particles")) w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.2, 0), 14, 0.25, 0.1, 0.25, 0.06);
        return true;
    }

    Block capTop(Block hands) {
        int max = plugin.getConfig().getInt("launch.max_cap_height");
        for (int i = 0; i < max; i++) {
            Block c = hands.getRelative(BlockFace.UP, i);
            if (!solid(c)) return null;
            if (canStand(c)) return c;
            if (plugin.rules.isHard(c)) return null;
        }
        return null;
    }

    void assist(Player p) {
        UUID id = p.getUniqueId();
        Integer left = plugin.assistLeft.get(id);
        if (left == null) return;
        if (p.isOnGround() || left <= 1) {
            plugin.assistLeft.remove(id);
            plugin.assistFace.remove(id);
            return;
        }
        plugin.assistLeft.put(id, left - 1);
        BlockFace f = plugin.assistFace.get(id);
        if (plugin.getConfig().getBoolean("effects.particles")) {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 2, 0.15, 0.05, 0.15, 0.01);
        }
        double speed = plugin.getConfig().getDouble("launch.assist_speed");
        Vector toward = new Vector(-f.getModX(), 0, -f.getModZ());
        if (speed <= 0 || collides(p.getWorld(), p.getBoundingBox().shift(toward.clone().multiply(0.3)))) return;
        Vector v = p.getVelocity();
        if (v.getY() <= 0 && collides(p.getWorld(), p.getBoundingBox().shift(0, -0.6, 0))) return;
        p.setVelocity(new Vector(toward.getX() * speed, v.getY(), toward.getZ() * speed));
    }
}
