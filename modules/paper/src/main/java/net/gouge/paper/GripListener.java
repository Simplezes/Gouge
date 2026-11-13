package net.gouge.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

final class GripListener implements Listener {

    private final GougePlugin plugin;

    GripListener(GougePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        plugin.dbg(p, "use " + a);
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s != null) {
            s.lastUse = plugin.now;
        } else {
            plugin.tryStart(p);
        }
        if (plugin.getConfig().getBoolean("block_other_hand") && plugin.sessions.containsKey(p.getUniqueId())
                && e.getItem() != null && !plugin.rules.isPickaxe(e.getItem()) && !holdsUse(e.getItem())) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && blockedWhileGripping(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (blockedWhileGripping(e.getPlayer())) e.setCancelled(true);
    }

    boolean blockedWhileGripping(Player p) {
        return plugin.getConfig().getBoolean("block_other_hand") && plugin.sessions.containsKey(p.getUniqueId());
    }

    static boolean holdsUse(ItemStack it) {
        Material m = it.getType();
        return m.isEdible() || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT
                || m == Material.SHIELD || m == Material.POTION || m == Material.MILK_BUCKET;
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null || plugin.now - s.softAt > 2) return;
        double dmg = Math.round(e.getDamage() * plugin.getConfig().getDouble("soft_fall_damage"));
        double cap = plugin.getConfig().getDouble("soft_fall_damage_cap");
        e.setDamage(cap > 0 ? Math.min(dmg, cap) : dmg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.end(e.getPlayer());
        plugin.prevY.remove(e.getPlayer().getUniqueId());
        plugin.tickVy.remove(e.getPlayer().getUniqueId());
        plugin.mantled.remove(e.getPlayer().getUniqueId());
        plugin.assistLeft.remove(e.getPlayer().getUniqueId());
        plugin.assistFace.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.end(e.getEntity());
    }
}
