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
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
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
    static final int ASSIST_TICKS = 26;

    record Hit(Block block, BlockFace face, Vector at) {}

    static final class Session {
        int lockRefresh, lastUse, hangEnd = -1, grabTick = -1, baseHang, sneakOff = -1, softAt = -100, launchCharge;
        boolean dual, wasSneaking, noGrav;
        Material toolType, lockedType;
        Block pin;
        BlockFace pinFace;
        Vector anchor;
        Double slideVy, softVy;
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
    private final Map<UUID, Integer> assistLeft = new HashMap<>();
    private final Map<UUID, BlockFace> assistFace = new HashMap<>();
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
        } else {
            tryStart(p);
        }
        if (getConfig().getBoolean("block_other_hand") && sessions.containsKey(p.getUniqueId())
                && e.getItem() != null && !isPickaxe(e.getItem()) && !holdsUse(e.getItem())) {
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

    private void lockOtherHand(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !getConfig().getBoolean("block_other_hand")) return;
        var inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand(), off = inv.getItemInOffHand();
        ItemStack tool = isPickaxe(main) ? main : isPickaxe(off) ? off : null;
        ItemStack other = tool == main ? off : main;
        if (tool == null || isPickaxe(other) || other.getType().isAir()) return;
        Material m = other.getType();
        if (s.lockedType != m) {
            if (s.lockedType != null) p.setCooldown(s.lockedType, 0);
            s.lockedType = m;
            s.lockRefresh = 0;
        }
        if (now >= s.lockRefresh) {
            p.setCooldown(m, 200);
            s.lockRefresh = now + 160;
        }
        if (p.isHandRaised()) {
            try {
                p.getClass().getMethod("clearActiveItem").invoke(p);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private boolean blockedWhileGripping(Player p) {
        return getConfig().getBoolean("block_other_hand") && sessions.containsKey(p.getUniqueId());
    }

    private static boolean holdsUse(ItemStack it) {
        Material m = it.getType();
        return m.isEdible() || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT
                || m == Material.SHIELD || m == Material.POTION || m == Material.MILK_BUCKET;
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
        assistLeft.remove(e.getPlayer().getUniqueId());
        assistFace.remove(e.getPlayer().getUniqueId());
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
            lockOtherHand(p);
            assist(p);
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
        if (s.lockedType != null) p.setCooldown(s.lockedType, 0);
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
        s.toolType = tool.getType();
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
        if (p.isOnGround() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isFlying()) return false;
        if (p.isHandRaised() && cfg.getBoolean("block_other_hand")) s.lastUse = now;
        if (now - s.lastUse > cfg.getInt("use_heartbeat_ticks")) {
            releaseCooldown(p, s);
            return false;
        }
        ItemStack tool = heldPickaxe(p);
        if (tool == null) {
            releaseCooldown(p, s);
            return false;
        }
        EquipmentSlot slot = slotOf(p);

        double drift = cfg.getDouble("max_drift");
        if (drift > 0) {
            Vector a = s.anchor;
            double dx = p.getLocation().getX() - a.getX(), dz = p.getLocation().getZ() - a.getZ();
            if (dx * dx + dz * dz > drift * drift) return false;
        }

        boolean space = climbInput(p);
        boolean canClimb = cfg.getBoolean("climbing.enabled") && (!cfg.getBoolean("climbing.requires_dual") || dualActive(p));
        int shim = space && canClimb ? 0 : shimmyDir(p, s);

        Hit hit = raycast(p);
        if (s.pin != null && s.hangEnd != -1 && ((space && canClimb) || shim != 0)) {
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
            s.softVy = null;
            gravity(p, s, false);
            if (Math.abs(effVy) < lock) {
                s.slideVy = null;
                p.setFallDistance(0f);
                boolean dual = dualActive(p);
                if (s.hangEnd == -1) {
                    s.grabTick = now;
                    s.pin = block;
                    s.pinFace = hit.face;
                    s.baseHang = Math.max(1, (int) Math.round(hangTicks(tool) * gripMult(block)));
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
                if (!(space && canClimb)) s.launchCharge = 0;
                if (space && canClimb) {
                    int r = climb(p, s, hit, slot, tool);
                    if (r == 2) return false;
                    if (r == 1) {
                        fx(p, s, hit, block, hardness);
                        return true;
                    }
                }
                dbg(p, "hang realVy=" + realVy);
                p.setVelocity(shim != 0 ? shimmyStep(p, s, tool, shim, hit, block, hardness) : new Vector());
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
            dbg(p, "hard slide realVy=" + realVy + " slideY=" + slideY + " vel=" + v.getY());
            p.setVelocity(new Vector(v.getX() * H_DAMP, slideY, v.getZ() * H_DAMP));
            p.setFallDistance(0f);
            fx(p, s, hit, block, hardness);
            return true;
        }

        s.hangEnd = -1;
        s.grabTick = -1;
        s.slideVy = null;
        if (realVy > 0.01) {
            s.softVy = null;
            gravity(p, s, true);
            fx(p, s, hit, block, hardness);
            return true;
        }
        gravity(p, s, false);
        s.softAt = now;
        if (now % 20 == 0 && !drain(p, slot, tool)) return false;
        double slide = clamp(SOFT_MAX - hardness * SOFT_SCALE, SOFT_MIN, SOFT_MAX);
        double ramp = cfg.getDouble("soft_slide_ramp");
        double slideVy = ramp <= 0 ? -slide : Math.max((s.softVy != null ? s.softVy : Math.min(realVy, 0)) - ramp, -slide);
        s.softVy = slideVy;
        dbg(p, "soft slide realVy=" + realVy + " cap=" + -slide + " vy=" + slideVy + " vel=" + v.getY());
        p.setVelocity(new Vector(v.getX() * H_DAMP, slideVy, v.getZ() * H_DAMP));
        fx(p, s, hit, block, hardness);
        return true;
    }

    private int climb(Player p, Session s, Hit hit, EquipmentSlot slot, ItemStack tool) {
        var cfg = getConfig();
        World w = p.getWorld();
        Location loc = p.getLocation();
        BlockFace face = s.pinFace;
        if (wallDistance(s.pin, face, loc) > cfg.getDouble("climbing.reach")) return 0;
        Block ledge = climbLedge(w, s.pin.getX(), s.pin.getZ(), loc.getY());
        if (ledge != null && !isHard(ledge) && tryLaunch(p, s, tool)) return 2;
        if (ledge == null || !isHard(ledge) || ledge.getType().getHardness() < 0) return 0;
        s.launchCharge = 0;

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

    private double spring(ItemStack tool) {
        return 1.0 + enchLevel(tool, "spring") * getConfig().getDouble("enchantments.spring_bonus");
    }

    private double gripMult(Block b) {
        var sec = getConfig().getConfigurationSection("block_grip");
        if (sec == null) return 1.0;
        String id = b.getType().getKey().toString();
        if (sec.isSet(id)) return sec.getDouble(id);
        for (String key : sec.getKeys(false)) {
            if (!key.startsWith("#")) continue;
            NamespacedKey k = NamespacedKey.fromString(key.substring(1));
            Tag<Material> t = k == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, k, Material.class);
            if (t != null && t.isTagged(b.getType())) return sec.getDouble(key);
        }
        return 1.0;
    }

    private int shimmyDir(Player p, Session s) {
        var cfg = getConfig();
        if (s.pin == null || s.pinFace == null || s.hangEnd == -1 || !cfg.getBoolean("traverse.enabled")
                || (cfg.getBoolean("traverse.requires_dual") && !dualActive(p))) return 0;
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double lx = -Math.sin(yaw), lz = Math.cos(yaw);
        double wx = -s.pinFace.getModX(), wz = -s.pinFace.getModZ();
        double angle = Math.toDegrees(Math.atan2(wx * lz - wz * lx, wx * lx + wz * lz));
        double min = cfg.getDouble("traverse.look_yaw");
        return angle < -min ? 1 : angle > min ? -1 : 0;
    }

    private Vector shimmyStep(Player p, Session s, ItemStack tool, int dir, Hit hit, Block hitBlock, float hardness) {
        var cfg = getConfig();
        BlockFace f = s.pinFace;
        double sp = cfg.getDouble("traverse.speed")
                * (1.0 + enchLevel(tool, "shimmy") * cfg.getDouble("enchantments.traverse_bonus"));
        Vector d = new Vector(-f.getModZ() * dir * sp, 0, f.getModX() * dir * sp);
        Location loc = p.getLocation();
        World w = p.getWorld();
        int x = f.getModX() != 0 ? s.pin.getX() : (int) Math.floor(loc.getX() + d.getX() * 4);
        int z = f.getModX() != 0 ? (int) Math.floor(loc.getZ() + d.getZ() * 4) : s.pin.getZ();
        Block body = w.getBlockAt(x, (int) Math.floor(loc.getY() + 1.0), z);
        if (!solid(body) || !isHard(body) || body.getType().getHardness() < 0 || collides(w, p.getBoundingBox().shift(d))) {
            return new Vector();
        }
        if (!body.equals(s.pin)) {
            int slot = 0;
            for (Block b : s.trail) crack(p, b, slot++, 0f);
            s.trail.clear();
        }
        s.pin = body;
        s.anchor.add(d);
        trail(p, s, body);
        if (now % 3 == 0) chips(wallContact(p, f), body, 5, 0.25, 0.04);
        if (now % 5 == 0) snd(loc, body.getBlockData().getSoundGroup().getHitSound(), 0.7f, 0.75f + (float) Math.random() * 0.3f);
        int cost = cfg.getInt("traverse.durability_cost");
        if (cost > 0 && now % 20 == 0) damage(p, slotOf(p), cost);
        return d;
    }

    private static double riseOf(double boost) {
        double y = 0, vy = boost;
        for (int i = 0; i < 400 && vy > 0; i++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
        }
        return y;
    }

    private static double boostFor(double rise) {
        double boost = 0.1;
        while (boost < 2.0 && riseOf(boost) < rise) boost += 0.01;
        return boost;
    }

    private void snd(Location at, Sound sound, float vol, float pitch) {
        float v = vol * (float) getConfig().getDouble("effects.volume");
        if (getConfig().getBoolean("effects.sounds") && v > 0) at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, v, pitch);
    }

    private void chips(Location at, Block block, int count, double spread, double speed) {
        if (getConfig().getBoolean("effects.particles")) {
            at.getWorld().spawnParticle(BLOCK_PARTICLE, at, count, spread, spread * 1.4, spread, speed, block.getBlockData());
        }
    }

    private Location wallContact(Player p, BlockFace face) {
        return p.getLocation().add(0, 1.0, 0).subtract(face.getModX() * 0.45, 0, face.getModZ() * 0.45);
    }

    private boolean tryLaunch(Player p, Session s, ItemStack tool) {
        var cfg = getConfig();
        World w = p.getWorld();
        Location loc = p.getLocation();
        Block hands = w.getBlockAt(s.pin.getX(), (int) Math.floor(loc.getY() + cfg.getDouble("climbing.speed") + 1.0), s.pin.getZ());
        Block cap = cfg.getBoolean("launch.enabled") && solid(hands) && !isHard(hands) ? capTop(hands) : null;
        if (cap == null) {
            s.launchCharge = 0;
            return false;
        }
        if (++s.launchCharge < (int) (cfg.getDouble("launch.charge_time") * 20)) {
            if (s.launchCharge % 2 == 1) chips(wallContact(p, s.pinFace), hands, 4, 0.2, 0.02);
            if (s.launchCharge % 4 == 1) snd(loc, hands.getBlockData().getSoundGroup().getHitSound(), 0.8f, 0.55f);
            return false;
        }
        s.launchCharge = 0;
        p.setFallDistance(0f);
        double base = cfg.getDouble("launch.upward_boost");
        if (cfg.getBoolean("launch.auto_height")) {
            base = Math.max(base, boostFor(ledgeTop(cap) - loc.getY() + 0.4));
        }
        p.setVelocity(new Vector(0, base * spring(tool), 0));
        mantled.put(p.getUniqueId(), now);
        assistLeft.put(p.getUniqueId(), ASSIST_TICKS);
        assistFace.put(p.getUniqueId(), s.pinFace);
        Location at = loc.clone().add(0, 1, 0).subtract(s.pinFace.getModX() * 0.4, 0, s.pinFace.getModZ() * 0.4);
        snd(loc, hands.getBlockData().getSoundGroup().getBreakSound(), 1.3f, 0.8f);
        snd(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.4f);
        chips(at, hands, 30, 0.3, 0.12);
        if (cfg.getBoolean("effects.particles")) w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.2, 0), 14, 0.25, 0.1, 0.25, 0.06);
        return true;
    }

    private Block capTop(Block hands) {
        int max = getConfig().getInt("launch.max_cap_height");
        for (int i = 0; i < max; i++) {
            Block c = hands.getRelative(BlockFace.UP, i);
            if (!solid(c)) return null;
            if (canStand(c)) return c;
            if (isHard(c)) return null;
        }
        return null;
    }

    private void assist(Player p) {
        UUID id = p.getUniqueId();
        Integer left = assistLeft.get(id);
        if (left == null) return;
        if (p.isOnGround() || left <= 1) {
            assistLeft.remove(id);
            assistFace.remove(id);
            return;
        }
        assistLeft.put(id, left - 1);
        BlockFace f = assistFace.get(id);
        if (getConfig().getBoolean("effects.particles")) {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 2, 0.15, 0.05, 0.15, 0.01);
        }
        double speed = getConfig().getDouble("launch.assist_speed");
        Vector toward = new Vector(-f.getModX(), 0, -f.getModZ());
        if (speed <= 0 || collides(p.getWorld(), p.getBoundingBox().shift(toward.clone().multiply(0.3)))) return;
        Vector v = p.getVelocity();
        p.setVelocity(new Vector(toward.getX() * speed, v.getY(), toward.getZ() * speed));
    }

    private boolean kick(Player p, ItemStack tool, Hit hit) {
        var cfg = getConfig();
        cooldown(p, tool);
        p.setFallDistance(0f);
        double sp = spring(tool);
        Vector n = faceVec(hit.face).multiply(cfg.getDouble("wall_jump.forward_boost") * sp)
                .setY(cfg.getDouble("wall_jump.upward_boost") * sp);
        p.setVelocity(n);
        Location at = hit.at.toLocation(p.getWorld());
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.2, 0.2, 0.2, 0.25);
        chips(wallContact(p, hit.face), hit.block, 20, 0.25, 0.12);
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
        if (cd > 0) setPickaxeCooldowns(p, cd, true);
    }

    private void releaseCooldown(Player p, Session s) {
        int ticks = (int) (getConfig().getDouble("release_cooldown") * 20);
        if (ticks <= 0 || hasMantled(p)) return;
        setPickaxeCooldowns(p, ticks, false);
        if (s.toolType != null && !p.hasCooldown(s.toolType)) p.setCooldown(s.toolType, ticks);
    }

    private void setPickaxeCooldowns(Player p, int ticks, boolean force) {
        var inv = p.getInventory();
        for (ItemStack it : new ItemStack[]{inv.getItemInMainHand(), inv.getItemInOffHand()}) {
            if (isPickaxe(it) && (force || !p.hasCooldown(it.getType()))) p.setCooldown(it.getType(), ticks);
        }
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
