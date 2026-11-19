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

    static boolean isMetal(Sound s) {
        return s.equals(Sound.BLOCK_METAL_HIT) || s.equals(Sound.BLOCK_COPPER_HIT)
                || s.equals(Sound.BLOCK_NETHERITE_BLOCK_HIT) || s.equals(Sound.BLOCK_ANVIL_HIT);
    }

    static boolean isDusty(Sound s) {
        return s.equals(Sound.BLOCK_SAND_HIT) || s.equals(Sound.BLOCK_GRAVEL_HIT) || s.equals(Sound.BLOCK_SNOW_HIT);
    }

    void warnLowDurability(Player p) {
        var it = p.getInventory().getItem(rules.slotOf(p));
        int max = it.getType().getMaxDurability();
        if (max <= 0 || !(it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d)
                || max - d.getDamage() > Math.max(8, max / 10)) return;
        Location at = p.getLocation();
        snd(at, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.6f);
    }

    void cling(Player p, Session s) {
        boolean timed = !plugin.getConfig().getBoolean("unlimited_grip");
        float left = timed ? (float) (s.hangEnd - plugin.now) / Math.max(1, s.hangEnd - s.grabTick) : 1f;
        float urgency = left >= 0.5f ? 0f : 1f - left / 0.5f;
        if (plugin.now - Math.max(s.clingAt, s.grabTick) < Math.round(20 - 10 * urgency)) return;
        s.clingAt = plugin.now;
        snd(p.getLocation(), Sound.BLOCK_CHAIN_STEP, 0.4f + 0.2f * urgency,
                0.8f + 0.5f * urgency + (float) Math.random() * 0.2f);
    }

    void fx(Player p, Session s, Hit hit, Block block, float hardness) {
        trail(p, s, block);
        if (plugin.now % 20 == 0) warnLowDurability(p);
        if (plugin.now % 5 != 0) return;
        Location at = hit.at().toLocation(p.getWorld());
        var snd = block.getBlockData().getSoundGroup().getHitSound();
        if (rules.isHard(block)) {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, (isMetal(snd) ? 1.1f : 0.6f) + (float) Math.random() * 0.2f);
            p.getWorld().spawnParticle(isMetal(snd) ? Particle.ELECTRIC_SPARK : Particle.CRIT, at, 2, 0.05, 0.05, 0.05, 0.05);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at.clone().add(hit.face().getModX() * 0.15, 0.3,
                    hit.face().getModZ() * 0.15), 8, 0.05, 0.3, 0.05, 0.08, block.getBlockData());
        } else {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 0.5f, 1f);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at, 6, 0.15, 0.15, 0.15, 0.0, block.getBlockData());
            if (isDusty(snd)) p.getWorld().spawnParticle(Particle.POOF, at, 2, 0.15, 0.15, 0.15, 0.01);
        }
    }
}
