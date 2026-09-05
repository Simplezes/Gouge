package net.gouge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GougeConfig {

    public static final Logger LOGGER = LoggerFactory.getLogger("gouge");
    public static ConfigData INSTANCE = new ConfigData();

    public static void load() {
        Path configDir = GougePlatform.get().configDir();
        Path configFile = configDir.resolve("gouge.toml");
        boolean isNew = !Files.exists(configFile);

        CommentedFileConfig config = CommentedFileConfig.builder(configFile, TomlFormat.instance())
                .preserveInsertionOrder()
                .sync()
                .build();
        try {
            config.load();
        } catch (Exception e) {
            LOGGER.error("Could not read gouge.toml, keeping previous settings", e);
        }

        INSTANCE = validate(read(config));

        if (isNew) {
            write(config, INSTANCE);
            config.save();
        }
        config.close();
        GougePhysics.invalidateCache();
    }

    private static double num(CommentedConfig c, String path, double def) {
        Object v = c.get(path);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    private static int inum(CommentedConfig c, String path, int def) {
        Object v = c.get(path);
        return (v instanceof Number n) ? n.intValue() : def;
    }

    private static boolean bool(CommentedConfig c, String path, boolean def) {
        Object v = c.get(path);
        return (v instanceof Boolean b) ? b : def;
    }

    private static ConfigData read(CommentedConfig c) {
        ConfigData d = new ConfigData();

        d.mechanics.reach = num(c, "mechanics.reach", d.mechanics.reach);
        d.mechanics.slip_cooldown = num(c, "mechanics.slip_cooldown", d.mechanics.slip_cooldown);
        d.mechanics.soft_fall_damage = num(c, "mechanics.soft_fall_damage", d.mechanics.soft_fall_damage);
        d.mechanics.soft_fall_damage_cap = num(c, "mechanics.soft_fall_damage_cap", d.mechanics.soft_fall_damage_cap);
        d.mechanics.trail_length = inum(c, "mechanics.trail_length", d.mechanics.trail_length);
        d.mechanics.client_prediction = bool(c, "mechanics.client_prediction", d.mechanics.client_prediction);
        d.mechanics.min_fall_distance = num(c, "mechanics.min_fall_distance", d.mechanics.min_fall_distance);
        d.mechanics.max_drift = num(c, "mechanics.max_drift", d.mechanics.max_drift);

        d.wall_jump.time_window = num(c, "wall_jump.time_window", d.wall_jump.time_window);
        d.wall_jump.forward_boost = num(c, "wall_jump.forward_boost", d.wall_jump.forward_boost);
        d.wall_jump.upward_boost = num(c, "wall_jump.upward_boost", d.wall_jump.upward_boost);

        d.enchantments.grip_bonus = num(c, "enchantments.grip_bonus", d.enchantments.grip_bonus);
        d.enchantments.momentum_reduction = num(c, "enchantments.momentum_reduction", d.enchantments.momentum_reduction);

        Object pickaxesRaw = c.get(List.of("pickaxes"));
        if (pickaxesRaw instanceof CommentedConfig pickaxes) {
            Map<String, PickaxeStats> parsed = new LinkedHashMap<>();
            for (var entry : pickaxes.entrySet()) {
                if (entry.getValue() instanceof CommentedConfig pc) {
                    parsed.put(entry.getKey(), new PickaxeStats(
                            num(pc, "hang_time", 3.0),
                            inum(pc, "damage_rate", 2)));
                }
            }
            if (!parsed.isEmpty()) {
                d.pickaxes = parsed;
            }
        }

        Object overridesRaw = c.get(List.of("block_overrides"));
        if (overridesRaw instanceof CommentedConfig overrides) {
            Map<String, String> parsed = new LinkedHashMap<>();
            for (var entry : overrides.entrySet()) {
                if (entry.getValue() instanceof String s) {
                    parsed.put(entry.getKey(), s);
                }
            }
            d.block_overrides = parsed;
        }
        return d;
    }

    private static void write(CommentedConfig c, ConfigData d) {
        c.setComment("mechanics", """
                 =======================================================
                 GOUGE CONFIGURATION

                 Tip: Save this file and run '/gouge reload' in-game!
                 Values outside the listed range are clamped on load.
                 =======================================================""");
        c.set("mechanics.reach", d.mechanics.reach);
        c.setComment("mechanics.reach", """
                 How far from a wall you can be and still grab onto it. Default is 2.5 blocks.
                 Increase this if grabbing feels too hard to trigger. Range: 0.5 - 32.0""");
        c.set("mechanics.slip_cooldown", d.mechanics.slip_cooldown);
        c.setComment("mechanics.slip_cooldown", """
                 After slipping off a wall, how long before you can grab again (in seconds).
                 Set to 0 to remove the cooldown entirely. Range: 0 - 600""");
        c.set("mechanics.soft_fall_damage", d.mechanics.soft_fall_damage);
        c.setComment("mechanics.soft_fall_damage", """
                 When you land on a soft block (dirt, grass, leaves, etc.) after sliding,
                 this multiplies the fall damage you take. 0.5 (50%) means half damage, 0.0 means none.
                 Range: 0.0 - 1.0""");
        c.set("mechanics.soft_fall_damage_cap", d.mechanics.soft_fall_damage_cap);
        c.setComment("mechanics.soft_fall_damage_cap", """
                 Maximum damage a soft-block slide can ever deal, in half-hearts (2 = 1 heart),
                 no matter how long the slide was. Set to 0 to disable the cap. Range: 0 - 40""");
        c.set("mechanics.trail_length", d.mechanics.trail_length);
        c.setComment("mechanics.trail_length", """
                 How many blocks get the cracking visual effect as you slide down a wall.
                 Higher = longer crack trail behind you. Set to 0 to disable. Range: 0 - 16""");
        c.set("mechanics.client_prediction", d.mechanics.client_prediction);
        c.setComment("mechanics.client_prediction", """
                 Lets your own game predict the wall slide instead of waiting for the server to
                 correct you every tick. This is what keeps the slide smooth instead of jittery.
                 This is a client-side setting: it only affects your own screen, never other players.
                 Turn it off if the slide ever fights you or looks wrong on a modded server.""");
        c.set("mechanics.min_fall_distance", d.mechanics.min_fall_distance);
        c.setComment("mechanics.min_fall_distance", """
                 How far you need to have already fallen before you can grab a wall (in blocks).
                 Stops a simple jump from letting you attach instantly - you actually need to be
                 falling first. Range: 0.0 - 32.0""");
        c.set("mechanics.max_drift", d.mechanics.max_drift);
        c.setComment("mechanics.max_drift", """
                 How far you can drift sideways away from the spot you grabbed before you slip off
                 (in blocks). Stops you from walking/gliding along a wall indefinitely.
                 Range: 0.0 - 16.0""");

        c.set("wall_jump.time_window", d.wall_jump.time_window);
        c.setComment("wall_jump.time_window", """
                 How quickly you need to double-tap crouch to trigger the wall jump (in seconds).
                 Tap crouch once to release, then tap it again within this window to jump.
                 Works on both hard and soft blocks. Range: 0.05 - 5.0""");
        c.set("wall_jump.forward_boost", d.wall_jump.forward_boost);
        c.setComment("wall_jump.forward_boost", """
                 How hard you get launched away from the wall horizontally when wall jumping.
                 1.4 is default. Lower this if ragdoll mods or other mods react badly to the speed change.
                 Range: 0.0 - 5.0""");
        c.set("wall_jump.upward_boost", d.wall_jump.upward_boost);
        c.setComment("wall_jump.upward_boost", """
                 How hard you get launched upward when wall jumping.
                 1.1 is default. This is what gives you height on the jump. Range: 0.0 - 5.0""");

        c.set("enchantments.grip_bonus", d.enchantments.grip_bonus);
        c.setComment("enchantments.grip_bonus", """
                 How many extra seconds of hang time you get per level of the Grip enchantment.
                 Grip II with 2.0 here gives you 4 extra seconds before slipping. Range: 0 - 60""");
        c.set("enchantments.momentum_reduction", d.enchantments.momentum_reduction);
        c.setComment("enchantments.momentum_reduction", """
                 How many seconds are removed from your slip cooldown per level of the Momentum enchantment.
                 Momentum III with 1.0 here cuts 3 seconds off your cooldown. Range: 0 - 60""");

        c.setComment("pickaxes", """
                 =======================================================
                 PICKAXE STATS
                 Controls how long each pickaxe lets you hang and how fast it breaks.
                 Durability drains while hanging on hard blocks AND while sliding on soft ones.

                 Keys can be:
                   - A specific item:  "minecraft:diamond_pickaxe"
                   - An item tag:      "#c:pickaxes"  (covers all pickaxes from that tag)
                   - "fallback"        used if nothing else matches

                 Priority order: Specific Item > Tag > fallback
                 Invalid keys are logged and skipped instead of crashing.
                 =======================================================""");
        boolean first = true;
        for (var entry : d.pickaxes.entrySet()) {
            List<String> hangPath = List.of("pickaxes", entry.getKey(), "hang_time");
            List<String> dmgPath = List.of("pickaxes", entry.getKey(), "damage_rate");
            c.set(hangPath, entry.getValue().hang_time);
            c.set(dmgPath, entry.getValue().damage_rate);
            if (first) {
                c.setComment(hangPath, " How long you can hang before slipping (seconds). Range: 0 - 3600");
                c.setComment(dmgPath, " How many durability points are drained per second. Range: 0 - 1000");
                first = false;
            }
        }

        c.set("block_overrides", c.createSubConfig());
        c.setComment("block_overrides", """
                 =======================================================
                 BLOCK OVERRIDES
                 Decides whether a block hangs like solid rock ("hard") or lets you
                 slide down it ("soft"). You do not need to list every block here.

                 By default:
                   - Vanilla Minecraft blocks: hard if they require the correct tool
                     for drops (stone, ores, bricks, ...), soft otherwise (sand, dirt,
                     wood, leaves, ...).
                   - Any block from another mod: hard, unless listed below.

                 Add an entry to override that default for a specific block, e.g.:
                   "create:andesite_casing" = "soft"
                   "minecraft:sandstone" = "hard"
                 Values must be "hard" or "soft".
                 =======================================================""");
        for (var entry : d.block_overrides.entrySet()) {
            c.set(List.of("block_overrides", entry.getKey()), entry.getValue());
        }
    }

    private static ConfigData validate(ConfigData c) {
        if (c.mechanics == null) {
            c.mechanics = new Mechanics();
        }
        if (c.wall_jump == null) {
            c.wall_jump = new WallJump();
        }
        if (c.enchantments == null) {
            c.enchantments = new Enchantments();
        }
        if (c.pickaxes == null) {
            c.pickaxes = new ConfigData().pickaxes;
        }
        if (c.block_overrides == null) {
            c.block_overrides = new LinkedHashMap<>();
        }

        c.mechanics.reach = clamp("mechanics.reach", c.mechanics.reach, 0.5, 32.0);
        c.mechanics.slip_cooldown = clamp("mechanics.slip_cooldown", c.mechanics.slip_cooldown, 0.0, 600.0);
        c.mechanics.soft_fall_damage = clamp("mechanics.soft_fall_damage", c.mechanics.soft_fall_damage, 0.0, 1.0);
        c.mechanics.soft_fall_damage_cap = clamp("mechanics.soft_fall_damage_cap", c.mechanics.soft_fall_damage_cap, 0.0, 40.0);
        c.mechanics.trail_length = (int) clamp("mechanics.trail_length", c.mechanics.trail_length, 0, 16);
        c.mechanics.min_fall_distance = clamp("mechanics.min_fall_distance", c.mechanics.min_fall_distance, 0.0, 32.0);
        c.mechanics.max_drift = clamp("mechanics.max_drift", c.mechanics.max_drift, 0.0, 16.0);

        c.wall_jump.time_window = clamp("wall_jump.time_window", c.wall_jump.time_window, 0.05, 5.0);
        c.wall_jump.forward_boost = clamp("wall_jump.forward_boost", c.wall_jump.forward_boost, 0.0, 5.0);
        c.wall_jump.upward_boost = clamp("wall_jump.upward_boost", c.wall_jump.upward_boost, 0.0, 5.0);

        c.enchantments.grip_bonus = clamp("enchantments.grip_bonus", c.enchantments.grip_bonus, 0.0, 60.0);
        c.enchantments.momentum_reduction = clamp("enchantments.momentum_reduction", c.enchantments.momentum_reduction, 0.0, 60.0);

        Iterator<Map.Entry<String, PickaxeStats>> it = c.pickaxes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PickaxeStats> entry = it.next();
            PickaxeStats stats = entry.getValue();
            if (entry.getKey() == null || entry.getKey().isEmpty() || stats == null) {
                LOGGER.warn("Ignoring malformed pickaxe entry in gouge.toml: {}", entry.getKey());
                it.remove();
                continue;
            }
            stats.hang_time = clamp("pickaxes." + entry.getKey() + ".hang_time", stats.hang_time, 0.0, 3600.0);
            stats.damage_rate = (int) clamp("pickaxes." + entry.getKey() + ".damage_rate", stats.damage_rate, 0, 1000);
        }

        Iterator<Map.Entry<String, String>> ov = c.block_overrides.entrySet().iterator();
        while (ov.hasNext()) {
            Map.Entry<String, String> entry = ov.next();
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null
                    || !(value.equalsIgnoreCase("hard") || value.equalsIgnoreCase("soft"))) {
                LOGGER.warn("Ignoring invalid block_overrides entry in gouge.toml: {} = {} (must be \"hard\" or \"soft\")",
                        key, value);
                ov.remove();
                continue;
            }
            entry.setValue(value.toLowerCase(java.util.Locale.ROOT));
        }
        return c;
    }

    private static double clamp(String name, double value, double min, double max) {
        if (Double.isNaN(value)) {
            LOGGER.warn("gouge.toml: {} is not a number, using {}", name, min);
            return min;
        }
        double clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            LOGGER.warn("gouge.toml: {} was {}, using {} instead (allowed range {} to {})",
                    name, value, clamped, min, max);
        }
        return clamped;
    }

    public static class ConfigData {

        public Mechanics mechanics = new Mechanics();
        public WallJump wall_jump = new WallJump();
        public Enchantments enchantments = new Enchantments();
        public Map<String, PickaxeStats> pickaxes = new LinkedHashMap<>();
        public Map<String, String> block_overrides = new LinkedHashMap<>();

        public ConfigData() {
            pickaxes.put("minecraft:wooden_pickaxe", new PickaxeStats(2.0, 1));
            pickaxes.put("minecraft:stone_pickaxe", new PickaxeStats(4.0, 2));
            pickaxes.put("minecraft:golden_pickaxe", new PickaxeStats(1.5, 1));
            pickaxes.put("minecraft:iron_pickaxe", new PickaxeStats(6.0, 3));
            pickaxes.put("minecraft:diamond_pickaxe", new PickaxeStats(10.0, 5));
            pickaxes.put("minecraft:netherite_pickaxe", new PickaxeStats(15.0, 7));
            pickaxes.put("fallback", new PickaxeStats(3.0, 2));
        }
    }

    public static class Mechanics {

        public double reach = 2.5;
        public double slip_cooldown = 5.0;
        public double soft_fall_damage = 0.5;
        public double soft_fall_damage_cap = 6.0;
        public int trail_length = 5;
        public boolean client_prediction = true;
        public double min_fall_distance = 1.5;
        public double max_drift = 1.5;
    }

    public static class WallJump {

        public double time_window = 0.7;
        public double forward_boost = 1.4;
        public double upward_boost = 1.1;
    }

    public static class Enchantments {

        public double grip_bonus = 2.0;
        public double momentum_reduction = 1.0;
    }

    public static class PickaxeStats {

        public double hang_time;
        public int damage_rate;

        public PickaxeStats() {
        }

        public PickaxeStats(double hang_time, int damage_rate) {
            this.hang_time = hang_time;
            this.damage_rate = damage_rate;
        }
    }
}
