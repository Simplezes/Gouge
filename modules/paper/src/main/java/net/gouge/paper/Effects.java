package net.gouge.paper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

final class Effects {

    private final GougePlugin plugin;
    private final Rules rules;

    Effects(GougePlugin plugin, Rules rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    static final Particle BLOCK_PARTICLE = blockParticle();

    static Particle blockParticle() {
        try {
            return Particle.valueOf("BLOCK");
        } catch (IllegalArgumentException e) {
            return Particle.valueOf("BLOCK_CRACK");
        }
    }

    void snd(Location at, Sound sound, float vol, float pitch) {
        float v = vol * (float) plugin.getConfig().getDouble("effects.volume");
        if (plugin.getConfig().getBoolean("effects.sounds") && v > 0) at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, v, pitch);
    }

    void chips(Location at, Block block, int count, double spread, double speed) {
        if (plugin.getConfig().getBoolean("effects.particles")) {
            at.getWorld().spawnParticle(BLOCK_PARTICLE, at, count, spread, spread * 1.4, spread, speed, block.getBlockData());
        }
    }

    static Location wallContact(Player p, BlockFace face) {
        return p.getLocation().add(0, 1.0, 0).subtract(face.getModX() * 0.45, 0, face.getModZ() * 0.45);
    }

    void trail(Player p, Session s, Block block) {
        int len = plugin.getConfig().getInt("trail_length");
        if (len <= 0 || block.equals(s.trail.peekFirst())) return;
        s.trail.addFirst(block);
        while (s.trail.size() > len) crack(p, s.trail.removeLast(), s.trail.size(), 0f);
        int slot = 0;
        for (Block b : s.trail) {
            crack(p, b, slot, (Math.max(1, 5 - slot) + 0.5f) / 10f);
            slot++;
        }
    }

    void crack(Player p, Block b, int slot, float progress) {
        p.sendBlockDamage(b.getLocation(), progress, -1 - (p.getEntityId() * 32 + slot));
    }

    void fx(Player p, Session s, Hit hit, Block block, float hardness) {
        trail(p, s, block);
        if (plugin.now % 5 != 0) return;
        Location at = hit.at().toLocation(p.getWorld());
        var snd = block.getBlockData().getSoundGroup().getHitSound();
        if (rules.isHard(block)) {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 0.6f, 0.6f + (float) Math.random() * 0.2f);
            p.getWorld().spawnParticle(Particle.CRIT, at, 2, 0.05, 0.05, 0.05, 0.05);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at.clone().add(hit.face().getModX() * 0.15, 0.3,
                    hit.face().getModZ() * 0.15), 8, 0.05, 0.3, 0.05, 0.08, block.getBlockData());
        } else {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 0.5f, 1f);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at, 6, 0.15, 0.15, 0.15, 0.0, block.getBlockData());
        }
    }
}
