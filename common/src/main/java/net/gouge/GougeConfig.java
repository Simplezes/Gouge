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

        if (isNew || hasMissingKeys(config, INSTANCE)) {
            write(config, INSTANCE);
            config.save();
        }
        config.close();
        GougePhysics.invalidateCache();
    }

    private static boolean hasMissingKeys(CommentedConfig existing, ConfigData data) {
        CommentedConfig fresh = CommentedConfig.inMemory();
        write(fresh, data);
        return missing(fresh, existing, List.of());
    }

    private static boolean missing(CommentedConfig fresh, CommentedConfig existing, List<String> parent) {
        for (var entry : fresh.entrySet()) {
            List<String> path = new java.util.ArrayList<>(parent);
            path.add(entry.getKey());
            if (!existing.contains(path)) {
                return true;
            }
            if (entry.getValue() instanceof CommentedConfig child && missing(child, existing, path)) {
                return true;
            }
        }
        return false;
    }

    public static void save() {
        Path file = GougePlatform.get().configDir().resolve("gouge.toml");
        CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance())
                .preserveInsertionOrder()
                .sync()
                .build();
        try {
            write(config, validate(INSTANCE));
            config.save();
        } finally {
            config.close();
        }
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
        d.mechanics.cling_everything = bool(c, "mechanics.cling_everything", d.mechanics.cling_everything);
        d.mechanics.dual_grip_enabled = bool(c, "mechanics.dual_grip_enabled", d.mechanics.dual_grip_enabled);
        d.mechanics.dual_grip_multiplier = num(c, "mechanics.dual_grip_multiplier", d.mechanics.dual_grip_multiplier);
        d.mechanics.unlimited_grip = bool(c, "mechanics.unlimited_grip", d.mechanics.unlimited_grip);
        d.mechanics.release_cooldown = num(c, "mechanics.release_cooldown", d.mechanics.release_cooldown);
        d.mechanics.use_mod_stamina = bool(c, "mechanics.use_mod_stamina", d.mechanics.use_mod_stamina);
        d.mechanics.stamina_hang_cost = num(c, "mechanics.stamina_hang_cost", d.mechanics.stamina_hang_cost);
        d.mechanics.stamina_climb_cost = num(c, "mechanics.stamina_climb_cost", d.mechanics.stamina_climb_cost);
        d.mechanics.stamina_shimmy_cost = num(c, "mechanics.stamina_shimmy_cost", d.mechanics.stamina_shimmy_cost);
        d.mechanics.block_other_hand = bool(c, "mechanics.block_other_hand", d.mechanics.block_other_hand);
        d.mechanics.soft_slide_ramp = num(c, "mechanics.soft_slide_ramp", d.mechanics.soft_slide_ramp);

        d.wall_jump.time_window = num(c, "wall_jump.time_window", d.wall_jump.time_window);
        d.wall_jump.forward_boost = num(c, "wall_jump.forward_boost", d.wall_jump.forward_boost);
        d.wall_jump.upward_boost = num(c, "wall_jump.upward_boost", d.wall_jump.upward_boost);

        d.climbing.enabled = bool(c, "climbing.enabled", d.climbing.enabled);
        d.climbing.requires_dual = bool(c, "climbing.requires_dual", d.climbing.requires_dual);
        d.climbing.speed = num(c, "climbing.speed", d.climbing.speed);
        d.climbing.reach = num(c, "climbing.reach", d.climbing.reach);
        d.climbing.allow_diagonal = bool(c, "climbing.allow_diagonal", d.climbing.allow_diagonal);
        d.climbing.durability_cost = inum(c, "climbing.durability_cost", d.climbing.durability_cost);
        d.climbing.damage_both_tools = bool(c, "climbing.damage_both_tools", d.climbing.damage_both_tools);
        d.climbing.stop_at_soft_block = bool(c, "climbing.stop_at_soft_block", d.climbing.stop_at_soft_block);

        d.traverse.enabled = bool(c, "traverse.enabled", d.traverse.enabled);
        d.traverse.requires_dual = bool(c, "traverse.requires_dual", d.traverse.requires_dual);
        d.traverse.speed = num(c, "traverse.speed", d.traverse.speed);
        d.traverse.durability_cost = inum(c, "traverse.durability_cost", d.traverse.durability_cost);

        d.launch.enabled = bool(c, "launch.enabled", d.launch.enabled);
        d.launch.charge_time = num(c, "launch.charge_time", d.launch.charge_time);
        d.launch.upward_boost = num(c, "launch.upward_boost", d.launch.upward_boost);
        d.launch.max_cap_height = inum(c, "launch.max_cap_height", d.launch.max_cap_height);
        d.launch.auto_height = bool(c, "launch.auto_height", d.launch.auto_height);
        d.launch.assist_speed = num(c, "launch.assist_speed", d.launch.assist_speed);

        d.effects.particles = bool(c, "effects.particles", d.effects.particles);
        d.effects.sounds = bool(c, "effects.sounds", d.effects.sounds);
        d.effects.volume = num(c, "effects.volume", d.effects.volume);

        d.enchantments.spring_bonus = num(c, "enchantments.spring_bonus", d.enchantments.spring_bonus);
        d.enchantments.traverse_bonus = num(c, "enchantments.traverse_bonus", d.enchantments.traverse_bonus);
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

        Object gripRaw = c.get(List.of("block_grip"));
        if (gripRaw instanceof CommentedConfig grip) {
            Map<String, Double> parsed = new LinkedHashMap<>();
            for (var entry : grip.entrySet()) {
                if (entry.getValue() instanceof Number n) {
                    parsed.put(entry.getKey(), n.doubleValue());
                }
            }
            d.block_grip = parsed;
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
                 How much open air needs to be below you to grab a wall (in blocks), checked with
                 an instant raycast rather than waiting for you to actually fall that far. Stops a
                 simple jump/step from letting you attach. Range: 0.0 - 32.0""");
        c.set("mechanics.max_drift", d.mechanics.max_drift);
        c.setComment("mechanics.max_drift", """
                 How far you can drift sideways away from the spot you grabbed before you slip off
                 (in blocks). Stops you from walking/gliding along a wall indefinitely.
                 Range: 0.0 - 16.0""");
        c.set("mechanics.cling_everything", d.mechanics.cling_everything);
        c.setComment("mechanics.cling_everything", """
                 Treat every block as a hard (hang-able) block, regardless of the vanilla
                 hard/soft default or the block_overrides list below. Turn this on if you
                 just want to cling to anything - leaves, wood, dirt, everything - without
                 listing each block individually. block_overrides entries still win over this.""");
        c.set("mechanics.dual_grip_enabled", d.mechanics.dual_grip_enabled);
        c.setComment("mechanics.dual_grip_enabled", """
                 Enables or disables dual-pickaxe grip. When holding a qualifying pickaxe
                 in both hands, your grip duration is extended by the multiplier below.""");
        c.set("mechanics.dual_grip_multiplier", d.mechanics.dual_grip_multiplier);
        c.setComment("mechanics.dual_grip_multiplier", """
                 Multiplier applied to hang time when gripping with pickaxes in both hands.
                 1.5 means 50% longer hang time. Range: 1.0 - 10.0""");
        c.set("mechanics.unlimited_grip", d.mechanics.unlimited_grip);
        c.setComment("mechanics.unlimited_grip", """
                 Sandbox setting: allows infinite hang duration on walls without slipping.
                 Disabled by default (false). Durability drain still applies unless damage_rate is 0.""");
        c.set("mechanics.release_cooldown", d.mechanics.release_cooldown);
        c.setComment("mechanics.release_cooldown", """
                 After letting go of a wall on purpose (releasing the use key or switching away
                 from the pickaxe), how long before you can grab again (in seconds). This makes
                 releasing cost some height. Never shortens a longer slip/wall-jump cooldown.
                 Set to 0 to disable. Range: 0 - 600""");
        c.set("mechanics.use_mod_stamina", d.mechanics.use_mod_stamina);
        c.setComment("mechanics.use_mod_stamina", """
                 When a stamina mod (ParCool) is installed, gripping, climbing and shimmying
                 spend its stamina instead of using the hang_time grip timer. You slip when it
                 runs out. Has no effect without such a mod. Set to false to always use hang_time.""");
        c.set("mechanics.stamina_hang_cost", d.mechanics.stamina_hang_cost);
        c.setComment("mechanics.stamina_hang_cost", """
                 Stamina spent per second while hanging, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.stamina_climb_cost", d.mechanics.stamina_climb_cost);
        c.setComment("mechanics.stamina_climb_cost", """
                 Stamina spent per second while climbing, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.stamina_shimmy_cost", d.mechanics.stamina_shimmy_cost);
        c.setComment("mechanics.stamina_shimmy_cost", """
                 Stamina spent per second while shimmying sideways, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.block_other_hand", d.mechanics.block_other_hand);
        c.setComment("mechanics.block_other_hand", """
                 While a pickaxe in one hand can grip a wall (or is gripping), items in the other
                 hand (bow, food, shield, etc.) cannot be used. Set to false to allow using both.""");
        c.set("mechanics.soft_slide_ramp", d.mechanics.soft_slide_ramp);
        c.setComment("mechanics.soft_slide_ramp", """
                 How quickly a soft-block slide speeds up, in blocks per tick of extra speed each
                 tick. Lower = gentler start. Set to 0 to jump straight to full slide speed.
                 Range: 0.0 - 1.0""");

        c.setComment("climbing", """
                 =======================================================
                 CLIMBING SETTINGS
                 Controls optional pickaxe climbing mechanics.
                 =======================================================""");
        c.set("climbing.enabled", d.climbing.enabled);
        c.setComment("climbing.enabled", """
                 Enables or disables climbing independently. When enabled, holding Space
                 while gripping a wall lets you climb up the surface.""");
        c.set("climbing.requires_dual", d.climbing.requires_dual);
        c.setComment("climbing.requires_dual", """
                 If true, climbing requires holding a pickaxe in both hands. If false,
                 a single pickaxe is sufficient to climb.""");
        c.set("climbing.speed", d.climbing.speed);
        c.setComment("climbing.speed", """
                 Upward movement speed while climbing (in blocks per tick).
                 Default is 0.08. Range: 0.01 - 0.5""");
        c.set("climbing.reach", d.climbing.reach);
        c.setComment("climbing.reach", """
                 Maximum wall reach distance allowed while climbing (in blocks).
                 Default is 2.5. Range: 0.5 - 32.0""");
        c.set("climbing.allow_diagonal", d.climbing.allow_diagonal);
        c.setComment("climbing.allow_diagonal", """
                 Allows moving sideways along the wall while climbing (diagonal climbing).
                 Default is false (vertical only).""");
        c.set("climbing.durability_cost", d.climbing.durability_cost);
        c.setComment("climbing.durability_cost", """
                 Extra durability drained per second while actively climbing.
                 Default is 1. Range: 0 - 100""");
        c.set("climbing.damage_both_tools", d.climbing.damage_both_tools);
        c.setComment("climbing.damage_both_tools", """
                 If true and two pickaxes are held, climbing durability damage is dealt
                 to both pickaxes. If false, only the active tool takes damage.""");
        c.set("climbing.stop_at_soft_block", d.climbing.stop_at_soft_block);
        c.setComment("climbing.stop_at_soft_block", """
                 If true, climbing stops when your hands reach a soft block (dirt, leaves, etc.)
                 so you cannot climb up through soft blocks. If false, soft blocks above do not stop you.""");

        c.setComment("effects", """
                 =======================================================
                 EFFECTS
                 Particles and sounds for shimmying, climbing, launching and gripping.
                 =======================================================""");
        c.set("effects.particles", d.effects.particles);
        c.setComment("effects.particles", " Show chip and dust particles. Gameplay is not affected.");
        c.set("effects.sounds", d.effects.sounds);
        c.setComment("effects.sounds", " Play grip, scrape and launch sounds. Gameplay is not affected.");
        c.set("effects.volume", d.effects.volume);
        c.setComment("effects.volume", " Volume multiplier for Gouge sounds. Range: 0.0 - 2.0");

        c.setComment("traverse", """
                 =======================================================
                 TRAVERSE SETTINGS
                 Shimmy sideways along a wall while hanging (hold A / D).
                 Uses the normal grip timer and durability drain.
                 =======================================================""");
        c.set("traverse.enabled", d.traverse.enabled);
        c.setComment("traverse.enabled", " Lets you move left and right along a wall while hanging on it.");
        c.set("traverse.requires_dual", d.traverse.requires_dual);
        c.setComment("traverse.requires_dual", " If true, traversing requires a pickaxe in both hands.");
        c.set("traverse.speed", d.traverse.speed);
        c.setComment("traverse.speed", " Sideways speed in blocks per tick. Range: 0.01 - 0.3");
        c.set("traverse.durability_cost", d.traverse.durability_cost);
        c.setComment("traverse.durability_cost", """
                 Extra durability drained per second while shimmying sideways, on top of the
                 normal hang drain. Default is 0. Range: 0 - 100""");

        c.setComment("launch", """
                 =======================================================
                 LAUNCH SETTINGS
                 When a climb is stopped by a short cap of soft blocks (dirt, grass...) on top
                 of the rock, keep holding Space and you spring up and over it.
                 =======================================================""");
        c.set("launch.enabled", d.launch.enabled);
        c.setComment("launch.enabled", " Enables the climb-over-soft-cap launch.");
        c.set("launch.charge_time", d.launch.charge_time);
        c.setComment("launch.charge_time", """
                 How long (seconds) you must keep holding Space against the soft cap before you
                 launch. Prevents accidental launches. Range: 0.0 - 5.0""");
        c.set("launch.upward_boost", d.launch.upward_boost);
        c.setComment("launch.upward_boost", """
                 Upward launch power used when auto_height is off. 0.75 clears about 2 blocks.
                 With auto_height on this value is ignored. Range: 0.1 - 2.0""");
        c.set("launch.auto_height", d.launch.auto_height);
        c.setComment("launch.auto_height", """
                 Sizes the launch to land you on top of the cap, so 1, 2, 3 or 4 block caps all
                 work without overshooting. Turn off to always use exactly upward_boost.""");
        c.set("launch.assist_speed", d.launch.assist_speed);
        c.setComment("launch.assist_speed", """
                 Gentle push towards the wall while you rise, so you land on top of the cap
                 instead of falling back. Set to 0 to disable. Range: 0.0 - 0.5""");
        c.set("launch.max_cap_height", d.launch.max_cap_height);
        c.setComment("launch.max_cap_height", """
                 Tallest soft cap (in blocks) you can launch over. The cap must have standable
                 ground on top, otherwise there is nothing to land on. Range: 1 - 4""");

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

        c.set("enchantments.spring_bonus", d.enchantments.spring_bonus);
        c.setComment("enchantments.spring_bonus", """
                 Extra launch and wall-kick power per level of the Spring enchantment.
                 0.15 means +15% per level. Range: 0 - 2""");
        c.set("enchantments.traverse_bonus", d.enchantments.traverse_bonus);
        c.setComment("enchantments.traverse_bonus", """
                 Extra shimmy speed per level of the Shimmy enchantment.
                 0.5 means +50% per level. Range: 0 - 5""");
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

        c.set("block_overrides", CommentedConfig.inMemory());
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

        c.set("block_grip", CommentedConfig.inMemory());
        c.setComment("block_grip", """
                 =======================================================
                 BLOCK GRIP
                 Multiplies hang time on specific blocks (or block tags). Optional.
                 1.0 = normal, 0.5 = slips twice as fast, 2.0 = holds twice as long.
                 Example:
                   "minecraft:ice" = 0.25
                   "#minecraft:logs" = 1.5
                 Range per entry: 0.0 - 10.0
                 =======================================================""");
        for (var entry : d.block_grip.entrySet()) {
            c.set(List.of("block_grip", entry.getKey()), entry.getValue());
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
        if (c.climbing == null) {
            c.climbing = new Climbing();
        }
        if (c.traverse == null) {
            c.traverse = new Traverse();
        }
        if (c.launch == null) {
            c.launch = new Launch();
        }
        if (c.effects == null) {
            c.effects = new Effects();
        }
        if (c.block_grip == null) {
            c.block_grip = new LinkedHashMap<>();
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
        c.mechanics.dual_grip_multiplier = clamp("mechanics.dual_grip_multiplier", c.mechanics.dual_grip_multiplier, 1.0, 10.0);
        c.mechanics.release_cooldown = clamp("mechanics.release_cooldown", c.mechanics.release_cooldown, 0.0, 600.0);
        c.mechanics.stamina_hang_cost = clamp("mechanics.stamina_hang_cost", c.mechanics.stamina_hang_cost, 0.0, 100.0);
        c.mechanics.stamina_climb_cost = clamp("mechanics.stamina_climb_cost", c.mechanics.stamina_climb_cost, 0.0, 100.0);
        c.mechanics.stamina_shimmy_cost = clamp("mechanics.stamina_shimmy_cost", c.mechanics.stamina_shimmy_cost, 0.0, 100.0);
        c.mechanics.soft_slide_ramp = clamp("mechanics.soft_slide_ramp", c.mechanics.soft_slide_ramp, 0.0, 1.0);

        c.wall_jump.time_window = clamp("wall_jump.time_window", c.wall_jump.time_window, 0.05, 5.0);
        c.wall_jump.forward_boost = clamp("wall_jump.forward_boost", c.wall_jump.forward_boost, 0.0, 5.0);
        c.wall_jump.upward_boost = clamp("wall_jump.upward_boost", c.wall_jump.upward_boost, 0.0, 5.0);

        c.climbing.speed = clamp("climbing.speed", c.climbing.speed, 0.01, 0.5);
        c.climbing.reach = clamp("climbing.reach", c.climbing.reach, 0.5, 32.0);
        c.climbing.durability_cost = (int) clamp("climbing.durability_cost", c.climbing.durability_cost, 0, 100);

        c.traverse.speed = clamp("traverse.speed", c.traverse.speed, 0.01, 0.3);
        c.traverse.durability_cost = (int) clamp("traverse.durability_cost", c.traverse.durability_cost, 0, 100);
        c.launch.assist_speed = clamp("launch.assist_speed", c.launch.assist_speed, 0.0, 0.5);
        c.effects.volume = clamp("effects.volume", c.effects.volume, 0.0, 2.0);
        c.launch.charge_time = clamp("launch.charge_time", c.launch.charge_time, 0.0, 5.0);
        c.launch.upward_boost = clamp("launch.upward_boost", c.launch.upward_boost, 0.1, 2.0);
        c.launch.max_cap_height = (int) clamp("launch.max_cap_height", c.launch.max_cap_height, 1, 4);
        c.enchantments.spring_bonus = clamp("enchantments.spring_bonus", c.enchantments.spring_bonus, 0.0, 2.0);
        c.enchantments.traverse_bonus = clamp("enchantments.traverse_bonus", c.enchantments.traverse_bonus, 0.0, 5.0);
        c.block_grip.replaceAll((k, v) -> clamp("block_grip." + k, v, 0.0, 10.0));
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
        public Climbing climbing = new Climbing();
        public Traverse traverse = new Traverse();
        public Launch launch = new Launch();
        public Effects effects = new Effects();
        public Map<String, Double> block_grip = new LinkedHashMap<>();
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
        public boolean cling_everything = false;
        public boolean dual_grip_enabled = true;
        public double dual_grip_multiplier = 1.5;
        public boolean unlimited_grip = false;
        public double release_cooldown = 0.5;
        public boolean use_mod_stamina = true;
        public double stamina_hang_cost = 4.0;
        public double stamina_climb_cost = 8.0;
        public double stamina_shimmy_cost = 8.0;
        public boolean block_other_hand = true;
        public double soft_slide_ramp = 0.05;
    }

    public static class Climbing {

        public boolean enabled = true;
        public boolean requires_dual = true;
        public double speed = 0.08;
        public double reach = 2.5;
        public boolean allow_diagonal = false;
        public int durability_cost = 1;
        public boolean damage_both_tools = true;
        public boolean stop_at_soft_block = true;
    }

    public static class Traverse {

        public boolean enabled = true;
        public boolean requires_dual = true;
        public double speed = 0.06;
        public int durability_cost = 0;
    }

    public static class Effects {

        public boolean particles = true;
        public boolean sounds = true;
        public double volume = 1.0;
    }

    public static class Launch {

        public boolean enabled = true;
        public double charge_time = 0.3;
        public double upward_boost = 0.75;
        public int max_cap_height = 3;
        public boolean auto_height = true;
        public double assist_speed = 0.15;
    }

    public static class WallJump {

        public double time_window = 0.7;
        public double forward_boost = 1.4;
        public double upward_boost = 1.1;
    }

    public static class Enchantments {

        public double spring_bonus = 0.15;
        public double traverse_bonus = 0.5;
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
