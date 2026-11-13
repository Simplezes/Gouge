package net.gouge.paper;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.gouge.paper.Effects.*;
import static net.gouge.paper.Geometry.*;
import static net.gouge.paper.Tuning.*;
public final class GougePlugin extends JavaPlugin {

    private boolean climbInput(Player p) {
        return p.getLocation().getPitch() < getConfig().getDouble("climbing.look_pitch");
    }

    final Map<UUID, Session> sessions = new HashMap<>();
    final Map<UUID, Double> prevY = new HashMap<>();
    final Map<UUID, Double> tickVy = new HashMap<>();
    final Map<UUID, Integer> mantled = new HashMap<>();
    final Map<UUID, Integer> assistLeft = new HashMap<>();
    final Map<UUID, BlockFace> assistFace = new HashMap<>();
    final Rules rules = new Rules(this);
    final Effects effects = new Effects(this, rules);
    final Launch launch = new Launch(this);
    final Shimmy shimmy = new Shimmy(this);
    final Release release = new Release(this);
    int now;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rules.load();
        getServer().getPluginManager().registerEvents(new GripListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 1, 1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            rules.load();
            sender.sendMessage("Gouge config reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase()) ? List.of("reload") : List.of();
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
    }

    private void lockOtherHand(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !getConfig().getBoolean("block_other_hand")) return;
        var inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand(), off = inv.getItemInOffHand();
        ItemStack tool = rules.isPickaxe(main) ? main : rules.isPickaxe(off) ? off : null;
        ItemStack other = tool == main ? off : main;
        if (tool == null || rules.isPickaxe(other) || other.getType().isAir()) return;
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
            launch.assist(p);
            double y = p.getLocation().getY();
            tickVy.put(id, y - prevY.getOrDefault(id, y));
            prevY.put(id, y);
        }
    }

    void dbg(Player p, String msg) {
        if (getConfig().getBoolean("debug")) getLogger().info("[" + p.getName() + "] " + msg);
    }

    void end(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.lockedType != null) p.setCooldown(s.lockedType, 0);
        if (s.noGrav) p.setGravity(true);
        int slot = 0;
        for (Block b : s.trail) effects.crack(p, b, slot++, 0f);
    }

    boolean hasMantled(Player p) {
        Integer at = mantled.get(p.getUniqueId());
        if (at == null) return false;
        if (now - at >= MANTLE_REGRAB) {
            mantled.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    void tryStart(Player p) {
        var cfg = getConfig();
        ItemStack tool = rules.heldPickaxe(p);
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
        float hardness = hit.block().getType().getHardness();
        if (hardness < 0) return;

        int damage = Math.round((float) (-vy * Math.min(hardness, 5f) * 3));
        if (damage > 0 && !rules.damage(p, rules.slotOf(p), damage)) return;

        Session s = new Session();
        s.toolType = tool.getType();
        s.lastUse = now;
        s.anchor = p.getLocation().toVector();
        sessions.put(p.getUniqueId(), s);

        Location at = hit.at().toLocation(p.getWorld());
        var snd = hit.block().getBlockData().getSoundGroup().getBreakSound();
        if (rules.isHard(hit.block())) {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 2f, 0.5f);
            p.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.2, 0.2, 0.2, 0.15);
        } else {
            p.getWorld().playSound(at, snd, SoundCategory.PLAYERS, 1.5f, 0.6f);
            p.getWorld().spawnParticle(BLOCK_PARTICLE, at, 25, 0.3, 0.3, 0.3, 0.0, hit.block().getBlockData());
        }
    }

    private boolean tick(Player p, Session s) {
        var cfg = getConfig();
        if (p.isOnGround() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isFlying()) return false;
        if (p.isHandRaised() && cfg.getBoolean("block_other_hand")) s.lastUse = now;
        if (now - s.lastUse > cfg.getInt("use_heartbeat_ticks")) {
            release.releaseCooldown(p, s);
            return false;
        }
        ItemStack tool = rules.heldPickaxe(p);
        if (tool == null) {
            release.releaseCooldown(p, s);
            return false;
        }
        EquipmentSlot slot = rules.slotOf(p);

        double drift = cfg.getDouble("max_drift");
        if (drift > 0) {
            Vector a = s.anchor;
            double dx = p.getLocation().getX() - a.getX(), dz = p.getLocation().getZ() - a.getZ();
            if (dx * dx + dz * dz > drift * drift) return false;
        }

        boolean space = climbInput(p);
        boolean canClimb = cfg.getBoolean("climbing.enabled") && (!cfg.getBoolean("climbing.requires_dual") || rules.dualActive(p));
        int shim = space && canClimb ? 0 : shimmy.shimmyDir(p, s);

        Hit hit = raycast(p);
        if (s.pin != null && s.hangEnd != -1 && ((space && canClimb) || shim != 0)) {
            Block focus = p.getWorld().getBlockAt(s.pin.getX(), (int) Math.floor(p.getLocation().getY() + 1), s.pin.getZ());
            Block b = focus.getType().isAir() || !rules.isHard(focus) ? s.pin : focus;
            if (!b.getType().isAir()) hit = new Hit(b, s.pinFace, center(b).add(faceVec(s.pinFace).multiply(0.5)));
        }
        if (hit == null || !hit.face().isCartesian() || hit.face().getModY() != 0) return false;
        Block block = hit.block();
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
            return !release.kick(p, tool, hit);
        }
        s.wasSneaking = sneak;

        double realVy = loc.getY() - prevY.getOrDefault(p.getUniqueId(), loc.getY());
        double effVy = s.slideVy != null ? s.slideVy : realVy;
        double lock = s.hangEnd != -1 ? rules.hangHold() : HANG_LOCK;
        Vector v = p.getVelocity();

        if (rules.isHard(block)) {
            s.softAt = -100;
            s.softVy = null;
            gravity(p, s, false);
            if (Math.abs(effVy) < lock) {
                s.slideVy = null;
                p.setFallDistance(0f);
                boolean dual = rules.dualActive(p);
                if (s.hangEnd == -1) {
                    s.grabTick = now;
                    s.pin = block;
                    s.pinFace = hit.face();
                    s.baseHang = Math.max(1, (int) Math.round(rules.hangTicks(tool) * rules.gripMult(block)));
                    s.dual = dual;
                    int ticks = dual ? (int) Math.round(s.baseHang * cfg.getDouble("dual_grip_multiplier")) : s.baseHang;
                    s.hangEnd = now + Math.max(1, ticks);
                } else if (s.dual && !dual) {
                    s.dual = false;
                    s.hangEnd = s.grabTick + s.baseHang;
                }
                if (!cfg.getBoolean("unlimited_grip") && now >= s.hangEnd) {
                    release.slip(p, tool, loc);
                    return false;
                }
                if (!(space && canClimb)) s.launchCharge = 0;
                if (space && canClimb) {
                    int r = climb(p, s, hit, slot, tool);
                    if (r == 2) return false;
                    if (r == 1) {
                        effects.fx(p, s, hit, block, hardness);
                        return true;
                    }
                }
                dbg(p, "hang realVy=" + realVy);
                p.setVelocity(shim != 0 ? shimmy.shimmyStep(p, s, tool, shim, hit, block, hardness) : new Vector());
                if (now % 20 == 0 && !rules.drain(p, slot, tool)) return false;
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
            effects.fx(p, s, hit, block, hardness);
            return true;
        }

        s.hangEnd = -1;
        s.grabTick = -1;
        s.slideVy = null;
        if (realVy > 0.01) {
            s.softVy = null;
            gravity(p, s, true);
            effects.fx(p, s, hit, block, hardness);
            return true;
        }
        gravity(p, s, false);
        s.softAt = now;
        if (now % 20 == 0 && !rules.drain(p, slot, tool)) return false;
        double slide = clamp(SOFT_MAX - hardness * SOFT_SCALE, SOFT_MIN, SOFT_MAX);
        double ramp = cfg.getDouble("soft_slide_ramp");
        double slideVy = ramp <= 0 ? -slide : Math.max((s.softVy != null ? s.softVy : Math.min(realVy, 0)) - ramp, -slide);
        s.softVy = slideVy;
        dbg(p, "soft slide realVy=" + realVy + " cap=" + -slide + " vy=" + slideVy + " vel=" + v.getY());
        p.setVelocity(new Vector(v.getX() * H_DAMP, slideVy, v.getZ() * H_DAMP));
        effects.fx(p, s, hit, block, hardness);
        return true;
    }

    private int climb(Player p, Session s, Hit hit, EquipmentSlot slot, ItemStack tool) {
        var cfg = getConfig();
        World w = p.getWorld();
        Location loc = p.getLocation();
        BlockFace face = s.pinFace;
        if (wallDistance(s.pin, face, loc) > cfg.getDouble("climbing.reach")) return 0;
        Block ledge = climbLedge(w, s.pin.getX(), s.pin.getZ(), loc.getY());
        Block hands = w.getBlockAt(s.pin.getX(), (int) Math.floor(loc.getY() + cfg.getDouble("climbing.speed") + 1.0), s.pin.getZ());
        if (solid(hands) && !rules.isHard(hands) && cfg.getBoolean("climbing.stop_at_soft_block")) {
            return launch.tryLaunch(p, s, tool) ? 2 : 0;
        }
        s.launchCharge = 0;
        if (ledge == null || ledge.getType().getHardness() < 0) return 0;

        if (rules.isHard(ledge) && canStand(ledge) && loc.getY() >= ledgeTop(ledge) - 0.45) {
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
            if (!rules.drain(p, slot, p.getInventory().getItem(slot))) return 2;
            int extra = cfg.getInt("climbing.durability_cost");
            if (extra > 0) {
                if (cfg.getBoolean("climbing.damage_both_tools") && rules.dualActive(p)) {
                    rules.damage(p, EquipmentSlot.HAND, extra);
                    rules.damage(p, EquipmentSlot.OFF_HAND, extra);
                } else {
                    rules.damage(p, slot, extra);
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

    private void gravity(Player p, Session s, boolean on) {
        p.setGravity(on);
        s.noGrav = !on;
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
}
