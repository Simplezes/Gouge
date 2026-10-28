package net.gouge.paper;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GougePlugin extends JavaPlugin implements Listener {
    static final double SOFT_MAX = 0.5, SOFT_MIN = 0.3, SOFT_SCALE = 0.13, H_DAMP = 0.5;
    static final double HARD_BASE = 0.90, HARD_SCALE = 0.03, HARD_MIN = 0.55, HARD_MAX = 0.90, HANG_LOCK = 0.08;
    static final int MANTLE_REGRAB = 20;

    record Hit(Block block, BlockFace face, Vector at) {}

    static final class Session {
        int lastUse, hangEnd = -1, grabTick = -1, baseHang, sneakOff = -1, softAt = -100;
        boolean dual, wasSneaking, noGrav;
        Block pin;
        BlockFace pinFace;
        Vector anchor;
        Double slideVy;
        final ArrayDeque<Block> trail = new ArrayDeque<>();
    }

    static final Particle BLOCK_PARTICLE = blockParticle();

    private static Particle blockParticle() {
        try {
            return Particle.valueOf("BLOCK");
        } catch (IllegalArgumentException e) {
            return Particle.valueOf("BLOCK_CRACK");
        }
    }

    private boolean climbInput(Player p) {
        return p.getLocation().getPitch() < getConfig().getDouble("climbing.look_pitch");
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Double> prevY = new HashMap<>();
    private final Map<UUID, Double> tickVy = new HashMap<>();
    private final Map<UUID, Integer> mantled = new HashMap<>();
    private final Map<Material, double[]> pickaxes = new EnumMap<>(Material.class);
    private final Map<String, Boolean> overrides = new HashMap<>();
    private double[] fallback;
    private int now;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 1, 1);
    }

    private void loadConfig() {
        pickaxes.clear();
        overrides.clear();
        var sec = getConfig().getConfigurationSection("pickaxes");
        fallback = pair(sec.getDoubleList("fallback"));
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) continue;
            double[] st = pair(sec.getDoubleList(m.getKey().toString()));
            if (st == null) {
                for (String key : sec.getKeys(false)) {
                    if (key.startsWith("#") && tagHas(key.substring(1), m)) {
                        st = pair(sec.getDoubleList(key));
                        break;
                    }
                }
            }
            if (st != null || Tag.ITEMS_PICKAXES.isTagged(m)) pickaxes.put(m, st != null ? st : fallback);
        }
        var ov = getConfig().getConfigurationSection("block_overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                String v = ov.getString(key, "");
                if (v.equals("hard") || v.equals("soft")) overrides.put(key, v.equals("hard"));
                else getLogger().warning("Ignoring block_overrides." + key + ": must be \"hard\" or \"soft\"");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadConfig();
            sender.sendMessage("Gouge config reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase()) ? List.of("reload") : List.of();
    }

    private static double[] pair(List<Double> l) {
        return l.size() < 2 ? null : new double[]{l.get(0), l.get(1)};
    }

    private static boolean tagHas(String key, Material m) {
        NamespacedKey k = NamespacedKey.fromString(key);
        Tag<Material> t = k == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, k, Material.class);
        return t != null && t.isTagged(m);
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        dbg(p, "use " + a);
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.lastUse = now;
            return;
        }
        tryStart(p);
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || now - s.softAt > 2) return;
        double dmg = Math.round(e.getDamage() * getConfig().getDouble("soft_fall_damage"));
        double cap = getConfig().getDouble("soft_fall_damage_cap");
        e.setDamage(cap > 0 ? Math.min(dmg, cap) : dmg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        end(e.getPlayer());
        prevY.remove(e.getPlayer().getUniqueId());
        tickVy.remove(e.getPlayer().getUniqueId());
        mantled.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        end(e.getEntity());
    }

    private void tickAll() {
        now++;
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Session s = sessions.get(id);
            if (p == null || !p.isOnline()) {
                sessions.remove(id);
            } else if (!tick(p, s)) {
                end(p);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.isOnGround() && mantled.containsKey(id)) {
                if (climbInput(p)) mantled.put(id, now);
                else mantled.remove(id);
            }
            double y = p.getLocation().getY();
            tickVy.put(id, y - prevY.getOrDefault(id, y));
            prevY.put(id, y);
        }
    }

    private void dbg(Player p, String msg) {
        if (getConfig().getBoolean("debug")) getLogger().info("[" + p.getName() + "] " + msg);
    }

    private void end(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.noGrav) p.setGravity(true);
        int slot = 0;
        for (Block b : s.trail) crack(p, b, slot++, 0f);
    }

    private boolean hasMantled(Player p) {
        Integer at = mantled.get(p.getUniqueId());
        if (at == null) return false;
        if (now - at >= MANTLE_REGRAB) {
            mantled.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    private void tryStart(Player p) {
        var cfg = getConfig();
        ItemStack tool = heldPickaxe(p);
        double vy = tickVy.getOrDefault(p.getUniqueId(), 0.0);
        dbg(p, "start? tool=" + (tool != null) + " ground=" + p.isOnGround() + " flying=" + p.isFlying()
                + " gliding=" + p.isGliding() + " vy=" + vy);
        if (tool == null || p.isOnGround() || p.isGliding() || p.isFlying() || p.isInsideVehicle()
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || hasMantled(p) || p.hasCooldown(tool.getType())) return;
        boolean clear = hasClearance(p, cfg.getDouble("min_fall_distance"));
        Hit hit = raycast(p);
        dbg(p, "clear=" + clear + " hit=" + hit);
        if (vy >= 0 || !clear) return;
        if (hit == null) return;
        float hardness = hit.block.getType().getHardness();
        if (hardness < 0) return;

        int damage = Math.round((float) (-vy * Math.min(hardness, 5f) * 3));
        if (damage > 0 && !damage(p, slotOf(p), damage)) return;

        Session s = new Session();
        s.lastUse = now;
        s.anchor = p.getLocation().toVector();
        sessions.put(p.getUniqueId(), s);

        Location at = hit.at.toLocation(p.getWorld());
        var snd = hit.block.getBlockData().getSoundGroup().getBreakSound();
        if (isHard(hit.block)) {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 2f, 0.5f);
            p.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.2, 0.2, 0.2, 0.15);
        } else {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 1.5f, 0.6f);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at, 25, 0.3, 0.3, 0.3, 0.0, hit.block.getBlockData());
        }
    }

    private boolean tick(Player p, Session s) {
        var cfg = getConfig();
        if (p.isOnGround() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isFlying()
                || now - s.lastUse > cfg.getInt("use_heartbeat_ticks")) return false;
        ItemStack tool = heldPickaxe(p);
        if (tool == null) return false;
        EquipmentSlot slot = slotOf(p);

        double drift = cfg.getDouble("max_drift");
        if (drift > 0) {
            Vector a = s.anchor;
            double dx = p.getLocation().getX() - a.getX(), dz = p.getLocation().getZ() - a.getZ();
            if (dx * dx + dz * dz > drift * drift) return false;
        }

        boolean space = climbInput(p);
        boolean canClimb = cfg.getBoolean("climbing.enabled") && (!cfg.getBoolean("climbing.requires_dual") || dualActive(p));

        Hit hit = raycast(p);
        if (s.pin != null && s.hangEnd != -1 && space && canClimb) {
            Block focus = p.getWorld().getBlockAt(s.pin.getX(), (int) Math.floor(p.getLocation().getY() + 1), s.pin.getZ());
            Block b = focus.getType().isAir() ? s.pin : focus;
            if (!b.getType().isAir()) hit = new Hit(b, s.pinFace, center(b).add(faceVec(s.pinFace).multiply(0.5)));
        }
        if (hit == null || !hit.face.isCartesian() || hit.face.getModY() != 0) return false;
        Block block = hit.block;
        float hardness = block.getType().getHardness();
        if (hardness < 0) return false;
        hardness = Math.max(0f, hardness);

        Location loc = p.getLocation();
        if (!space && canStand(block) && loc.getY() >= ledgeTop(block) - 0.05) return false;

        boolean sneak = p.isSneaking();
        if (s.wasSneaking && !sneak) s.sneakOff = now;
        if (!s.wasSneaking && sneak && s.sneakOff != -1
                && now - s.sneakOff <= (int) (cfg.getDouble("wall_jump.time_window") * 20)) {
            s.sneakOff = -1;
            return !kick(p, tool, hit);
        }
        s.wasSneaking = sneak;

        double realVy = loc.getY() - prevY.getOrDefault(p.getUniqueId(), loc.getY());
        double effVy = s.slideVy != null ? s.slideVy : realVy;
        double lock = s.hangEnd != -1 ? hangHold() : HANG_LOCK;
        Vector v = p.getVelocity();

        if (isHard(block)) {
            s.softAt = -100;
            gravity(p, s, false);
            if (Math.abs(effVy) < lock) {
                s.slideVy = null;
                p.setFallDistance(0f);
                boolean dual = dualActive(p);
                if (s.hangEnd == -1) {
                    s.grabTick = now;
                    s.pin = block;
                    s.pinFace = hit.face;
                    s.baseHang = hangTicks(tool);
                    s.dual = dual;
                    int ticks = dual ? (int) Math.round(s.baseHang * cfg.getDouble("dual_grip_multiplier")) : s.baseHang;
                    s.hangEnd = now + Math.max(1, ticks);
                } else if (s.dual && !dual) {
                    s.dual = false;
                    s.hangEnd = s.grabTick + s.baseHang;
                }
                if (!cfg.getBoolean("unlimited_grip") && now >= s.hangEnd) {
                    slip(p, tool, loc);
                    return false;
                }
                if (space && canClimb) {
                    int r = climb(p, s, hit, slot);
                    if (r == 2) return false;
                    if (r == 1) {
                        fx(p, s, hit, block, hardness);
                        return true;
                    }
                }
                p.setVelocity(new Vector());
                if (now % 20 == 0 && !drain(p, slot, tool)) return false;
                if (now % 20 == 10) {
                    p.getWorld().playSound(loc, Sound.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.4f,
                            0.8f + (float) Math.random() * 0.2f);
                }
                return true;
            }
            s.hangEnd = -1;
            s.grabTick = -1;
            double f = clamp(HARD_BASE - hardness * HARD_SCALE, HARD_MIN, HARD_MAX);
            double slideY = (s.slideVy != null ? s.slideVy : Math.min(realVy, 0)) * f;
            s.slideVy = slideY;
            p.setVelocity(new Vector(v.getX() * H_DAMP, slideY, v.getZ() * H_DAMP));
            p.setFallDistance(0f);
            fx(p, s, hit, block, hardness);
            return true;
        }

        s.hangEnd = -1;
        s.grabTick = -1;
        s.slideVy = null;
        if (realVy > 0.01) {
            gravity(p, s, true);
            fx(p, s, hit, block, hardness);
            return true;
        }
        gravity(p, s, false);
        s.softAt = now;
        if (now % 20 == 0 && !drain(p, slot, tool)) return false;
        double slide = clamp(SOFT_MAX - hardness * SOFT_SCALE, SOFT_MIN, SOFT_MAX);
        p.setVelocity(new Vector(v.getX() * H_DAMP, -slide, v.getZ() * H_DAMP));
        fx(p, s, hit, block, hardness);
        return true;
    }

    private int climb(Player p, Session s, Hit hit, EquipmentSlot slot) {
        var cfg = getConfig();
        World w = p.getWorld();
        Location loc = p.getLocation();
        BlockFace face = s.pinFace;
        if (wallDistance(s.pin, face, loc) > cfg.getDouble("climbing.reach")) return 0;
        Block ledge = climbLedge(w, s.pin.getX(), s.pin.getZ(), loc.getY());
        if (ledge == null || !isHard(ledge) || ledge.getType().getHardness() < 0) return 0;

        if (canStand(ledge) && loc.getY() >= ledgeTop(ledge) - 0.45) {
            double dx = ledge.getX() + 0.5 - loc.getX(), dz = ledge.getZ() + 0.5 - loc.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double fx = len > 1e-4 ? dx / len : -face.getModX(), fz = len > 1e-4 ? dz / len : -face.getModZ();
            double speed = clamp(len / 6.0, 0.12, 0.3);
            p.setVelocity(new Vector(fx * speed, 0.47, fz * speed));
            mantled.put(p.getUniqueId(), now);
            w.playSound(loc, Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 0.6f, 1.2f);
            return 2;
        }
        if (climbCapped(w, ledge, loc)) return 0;

        double dy = cfg.getDouble("climbing.speed");
        if (collides(w, p.getBoundingBox().shift(0, dy, 0))) return 0;
        p.setVelocity(new Vector(0, dy, 0));

        if (now % 20 == 0) {
            if (!drain(p, slot, p.getInventory().getItem(slot))) return 2;
            int extra = cfg.getInt("climbing.durability_cost");
            if (extra > 0) {
                if (cfg.getBoolean("climbing.damage_both_tools") && dualActive(p)) {
                    damage(p, EquipmentSlot.HAND, extra);
                    damage(p, EquipmentSlot.OFF_HAND, extra);
                } else {
                    damage(p, slot, extra);
                }
                if (p.getInventory().getItem(slot).getType().isAir()) return 2;
            }
        }
        if (now % 10 == 0) {
            Location c = loc.clone().add(0, 1, 0).subtract(face.getModX() * 0.4, 0, face.getModZ() * 0.4);
            w.playSound(loc, Sound.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.4f, 1.2f + (float) Math.random() * 0.2f);
            w.spawnParticle(Particle.CRIT, c, 2, 0.05, 0.05, 0.05, 0.05);
        }
        return 1;
    }

    private boolean kick(Player p, ItemStack tool, Hit hit) {
        var cfg = getConfig();
        cooldown(p, tool);
        p.setFallDistance(0f);
        Vector n = faceVec(hit.face).multiply(cfg.getDouble("wall_jump.forward_boost"))
                .setY(cfg.getDouble("wall_jump.upward_boost"));
        p.setVelocity(n);
        Location at = hit.at.toLocation(p.getWorld());
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.2, 0.2, 0.2, 0.25);
        return true;
    }

    private void slip(Player p, ItemStack tool, Location at) {
        cooldown(p, tool);
        p.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.CRIT, at, 30, 0.3, 0.3, 0.3, 0.2);
    }

    private void cooldown(Player p, ItemStack tool) {
        var cfg = getConfig();
        int base = (int) (cfg.getDouble("slip_cooldown") * 20);
        int red = (int) (cfg.getDouble("enchantments.momentum_reduction") * 20);
        int cd = Math.max(0, base - enchLevel(tool, "momentum") * red);
        if (cd > 0) p.setCooldown(tool.getType(), cd);
    }

    private void trail(Player p, Session s, Block block) {
        int len = getConfig().getInt("trail_length");
        if (len <= 0 || block.equals(s.trail.peekFirst())) return;
        s.trail.addFirst(block);
        while (s.trail.size() > len) crack(p, s.trail.removeLast(), s.trail.size(), 0f);
        int slot = 0;
        for (Block b : s.trail) {
            crack(p, b, slot, (Math.max(1, 5 - slot) + 0.5f) / 10f);
            slot++;
        }
    }

    private void crack(Player p, Block b, int slot, float progress) {
        p.sendBlockDamage(b.getLocation(), progress, -1 - (p.getEntityId() * 32 + slot));
    }

    private void fx(Player p, Session s, Hit hit, Block block, float hardness) {
        trail(p, s, block);
        if (now % 5 != 0) return;
        Location at = hit.at.toLocation(p.getWorld());
        var snd = block.getBlockData().getSoundGroup().getHitSound();
        if (isHard(block)) {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 0.6f, 0.6f + (float) Math.random() * 0.2f);
            p.getWorld().spawnParticle(Particle.CRIT, at, 2, 0.05, 0.05, 0.05, 0.05);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at.clone().add(hit.face.getModX() * 0.15, 0.3,
                    hit.face.getModZ() * 0.15), 8, 0.05, 0.3, 0.05, 0.08, block.getBlockData());
        } else {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 0.5f, 1f);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at, 6, 0.15, 0.15, 0.15, 0.0, block.getBlockData());
        }
    }

    private void gravity(Player p, Session s, boolean on) {
        p.setGravity(on);
        s.noGrav = !on;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double hangHold() {
        var c = getConfig();
        return Math.max(HANG_LOCK * 2, c.getBoolean("climbing.enabled") ? c.getDouble("climbing.speed") * 1.5 : 0);
    }

    private boolean isPickaxe(ItemStack it) {
        return it != null && pickaxes.containsKey(it.getType());
    }

    private boolean dualActive(Player p) {
        var inv = p.getInventory();
        return getConfig().getBoolean("dual_grip_enabled")
                && isPickaxe(inv.getItemInMainHand()) && isPickaxe(inv.getItemInOffHand());
    }

    private ItemStack heldPickaxe(Player p) {
        var inv = p.getInventory();
        if (isPickaxe(inv.getItemInMainHand())) return inv.getItemInMainHand();
        return isPickaxe(inv.getItemInOffHand()) ? inv.getItemInOffHand() : null;
    }

    private EquipmentSlot slotOf(Player p) {
        return isPickaxe(p.getInventory().getItemInMainHand()) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
    }

    private boolean damage(Player p, EquipmentSlot slot, int amount) {
        ItemStack it = p.getInventory().getItem(slot);
        if (!(it.getItemMeta() instanceof Damageable d) || it.getType().getMaxDurability() <= 0) return true;
        int unb = enchLevel(it, "minecraft", "unbreaking");
        int dmg = 0;
        for (int i = 0; i < amount; i++) {
            if (unb == 0 || Math.random() < 1.0 / (unb + 1)) dmg++;
        }
        if (dmg == 0) return true;
        d.setDamage(d.getDamage() + dmg);
        if (d.getDamage() >= it.getType().getMaxDurability()) {
            p.getInventory().setItem(slot, null);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
            return false;
        }
        it.setItemMeta(d);
        return true;
    }

    private static boolean collides(World w, BoundingBox box) {
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

    private boolean drain(Player p, EquipmentSlot slot, ItemStack tool) {
        int rate = (int) stats(tool)[1];
        return rate <= 0 || damage(p, slot, rate);
    }

    private double[] stats(ItemStack tool) {
        return pickaxes.getOrDefault(tool.getType(), fallback);
    }

    private int enchLevel(ItemStack it, String name) {
        return enchLevel(it, "gouge", name);
    }

    private static int enchLevel(ItemStack it, String ns, String name) {
        Enchantment e = Registry.ENCHANTMENT.get(new NamespacedKey(ns, name));
        return e == null ? 0 : it.getEnchantmentLevel(e);
    }

    private int hangTicks(ItemStack tool) {
        int base = (int) (stats(tool)[0] * 20);
        int bonus = (int) (getConfig().getDouble("enchantments.grip_bonus") * 20);
        return base + enchLevel(tool, "grip") * bonus;
    }

    private boolean isHard(Block b) {
        Boolean o = overrides.isEmpty() ? null : overrides.get(b.getType().getKey().toString());
        if (o != null) return o;
        return getConfig().getBoolean("cling_everything") || b.getBlockData().requiresCorrectToolForDrops();
    }

    private boolean hasClearance(Player p, double min) {
        return min <= 0 || p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), min,
                FluidCollisionMode.NEVER, true) == null;
    }

    private static boolean solid(Block b) {
        return !b.getCollisionShape().getBoundingBoxes().isEmpty();
    }

    private static boolean canStand(Block b) {
        return solid(b) && !solid(b.getRelative(BlockFace.UP)) && !solid(b.getRelative(BlockFace.UP, 2));
    }

    private static double ledgeTop(Block b) {
        double max = 1.0;
        var boxes = b.getCollisionShape().getBoundingBoxes();
        if (!boxes.isEmpty()) {
            max = 0;
            for (BoundingBox bb : boxes) max = Math.max(max, bb.getMaxY());
        }
        return b.getY() + max;
    }

    private static Vector center(Block b) {
        return new Vector(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    }

    private static Vector faceVec(BlockFace f) {
        return new Vector(f.getModX(), f.getModY(), f.getModZ());
    }

    private static double wallDistance(Block pin, BlockFace face, Location at) {
        boolean x = face.getModX() != 0;
        int step = x ? face.getModX() : face.getModZ();
        double plane = (x ? pin.getX() : pin.getZ()) + 0.5 + step * 0.5;
        return ((x ? at.getX() : at.getZ()) - plane) * step;
    }

    private static Block climbLedge(World w, int px, int pz, double feetY) {
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

    private static boolean climbCapped(World w, Block ledge, Location at) {
        if (!solid(ledge.getRelative(BlockFace.UP)) && solid(ledge.getRelative(BlockFace.UP, 2))) return true;
        for (int y = (int) Math.floor(at.getY() + 1.81); y <= ledge.getY() + 2; y++) {
            if (solid(w.getBlockAt(at.getBlockX(), y, at.getBlockZ()))) return true;
        }
        return false;
    }

    private Hit raycast(Player p) {
        double reach = getConfig().getDouble("reach");
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection();
        Hit h = resolve(p, p.getWorld().rayTraceBlocks(eye, look, reach, FluidCollisionMode.NEVER, true));
        if (h != null) return h;
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1e-6) return null;
        return resolve(p, p.getWorld().rayTraceBlocks(eye, flat.normalize(), reach, FluidCollisionMode.NEVER, true));
    }

    private static Hit resolve(Player p, RayTraceResult r) {
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
