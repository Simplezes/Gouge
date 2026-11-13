package net.gouge.paper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import static net.gouge.paper.Effects.*;
import static net.gouge.paper.Geometry.*;

final class Release {

    private final GougePlugin plugin;

    Release(GougePlugin plugin) {
        this.plugin = plugin;
    }

    boolean kick(Player p, ItemStack tool, Hit hit) {
        var cfg = plugin.getConfig();
        cooldown(p, tool);
        p.setFallDistance(0f);
        double sp = plugin.rules.spring(tool);
        Vector n = faceVec(hit.face()).multiply(cfg.getDouble("wall_jump.forward_boost") * sp)
                .setY(cfg.getDouble("wall_jump.upward_boost") * sp);
        p.setVelocity(n);
        Location at = hit.at().toLocation(p.getWorld());
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.2, 0.2, 0.2, 0.25);
        plugin.effects.chips(wallContact(p, hit.face()), hit.block(), 20, 0.25, 0.12);
        return true;
    }

    void slip(Player p, ItemStack tool, Location at) {
        cooldown(p, tool);
        p.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.CRIT, at, 30, 0.3, 0.3, 0.3, 0.2);
    }

    void cooldown(Player p, ItemStack tool) {
        var cfg = plugin.getConfig();
        int base = (int) (cfg.getDouble("slip_cooldown") * 20);
        int red = (int) (cfg.getDouble("enchantments.momentum_reduction") * 20);
        int cd = Math.max(0, base - plugin.rules.enchLevel(tool, "momentum") * red);
        if (cd > 0) setPickaxeCooldowns(p, cd, true);
    }

    void releaseCooldown(Player p, Session s) {
        int ticks = (int) (plugin.getConfig().getDouble("release_cooldown") * 20);
        if (ticks <= 0 || plugin.hasMantled(p)) return;
        setPickaxeCooldowns(p, ticks, false);
        if (s.toolType != null && !p.hasCooldown(s.toolType)) p.setCooldown(s.toolType, ticks);
    }

    void setPickaxeCooldowns(Player p, int ticks, boolean force) {
        var inv = p.getInventory();
        for (ItemStack it : new ItemStack[]{inv.getItemInMainHand(), inv.getItemInOffHand()}) {
            if (plugin.rules.isPickaxe(it) && (force || !p.hasCooldown(it.getType()))) p.setCooldown(it.getType(), ticks);
        }
    }
}
