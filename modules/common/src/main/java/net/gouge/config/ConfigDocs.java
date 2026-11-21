package net.gouge.config;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ConfigDocs {

    public static final Map<String, String[]> SECTIONS = new LinkedHashMap<>();
    private static final Map<String, String> KEYS = new LinkedHashMap<>();
    private static final Map<String, String> GROUPS = new LinkedHashMap<>();
    private static final Map<String, String> UNITS = new LinkedHashMap<>();
    private static String current;

    static {
        section("grip", "Grip", "Grabbing a wall with a pickaxe and staying on it.");
        key("reach", "How far from a wall you can be and still grab it, in blocks.");
        key("min_fall_distance", "Open air needed below you to grab, in blocks. Stops a plain jump from latching on.");
        key("max_drift", "How far you can drift sideways from where you grabbed before slipping, in blocks.");
        key("slip_cooldown", "Seconds before you can grab again after slipping off. 0 removes the cooldown.");
        key("release_cooldown", "Seconds before you can grab again after letting go on purpose. Never shortens a longer slip cooldown.");
        key("unlimited", "Sandbox: never slip from a timer. Pickaxe durability still drains.");
        key("cling_everything", "Treat every block as hard (hang-able). Entries in block_overrides still win.");
        key("block_other_hand", "While a pickaxe can grip or is gripping, the other hand cannot use items (bow, food, shield).");
        key("client_prediction", "Client-side: predict the slide on your own screen to keep it smooth. Turn off if it looks wrong on a modded server.");
        key("dual_enabled", "Holding a pickaxe in both hands extends hang time.");
        key("dual_multiplier", "Hang time multiplier with two pickaxes. 1.5 means 50% longer.");

        group("Grab", "reach", "min_fall_distance", "max_drift");
        group("Cooldowns", "slip_cooldown", "release_cooldown");
        group("Dual pickaxes", "dual_enabled", "dual_multiplier");
        group("Rules", "unlimited", "cling_everything", "block_other_hand", "client_prediction");
        units(" blocks", "reach", "min_fall_distance", "max_drift");
        units(" s", "slip_cooldown", "release_cooldown");
        units("x", "dual_multiplier");

        section("slide", "Soft-block slide", "Sliding down soft blocks (dirt, sand, leaves...).");
        key("fall_damage", "Fall damage multiplier when landing on a soft block after sliding. 0.5 is half, 0 is none.");
        key("fall_damage_cap", "Most damage a soft-block slide can deal, in half-hearts (2 = 1 heart). 0 disables the cap.");
        key("trail_length", "Blocks that show the crack trail behind you while sliding. 0 disables it.");
        key("ramp", "How much the slide speeds up each tick. 0 jumps straight to full speed.");

        units("x", "fall_damage");
        units(" blocks", "trail_length");

        section("stamina", "Stamina (Experimental)", "EXPERIMENTAL: may change or be removed. Off by default.");
        key("enabled", "Use Gouge's own stamina pool, carried over between grips. Hanging, climbing and shimmying drain it, it refills over time and the wheel is shown. Off: every grab starts with a full hang timer. Ignored when a stamina mod provides the stamina or grip.unlimited is on.");
        key("use_mod", "When Paraglider or ParCool is installed, spend its stamina instead of the hang timer. No effect without such a mod.");
        key("hang_cost", "Stamina spent per second while gripping, as a percent of the bar. The cost rises steeply toward 100. Also applies to Paraglider/ParCool.");
        key("climb_multiplier", "Drain while climbing compared to hanging. 2 is twice as fast, 0 makes climbing free.");
        key("shimmy_multiplier", "Drain while shimmying sideways compared to hanging.");
        key("refill_time", "Seconds to refill an empty pool while not gripping. 0 refills instantly.");
        key("refill_delay", "Seconds to wait after letting go before refilling starts.");

        group("Stamina pool", "enabled", "refill_time", "refill_delay");
        group("Drain", "hang_cost", "climb_multiplier", "shimmy_multiplier");
        group("Stamina mods", "use_mod");
        units(" s", "refill_time", "refill_delay");
        units("%", "hang_cost");
        units("x", "climb_multiplier", "shimmy_multiplier");

        section("climbing", "Climbing", "Hold Space while gripping to climb up the wall.");
        key("enabled", "Allow climbing.");
        key("requires_dual", "Require a pickaxe in both hands. Off: one is enough.");
        key("speed", "Upward speed in blocks per tick.");
        key("reach", "Wall reach while climbing, in blocks.");
        key("allow_diagonal", "Allow moving sideways while climbing. Off: vertical only.");
        key("durability_cost", "Extra durability drained per second while climbing.");
        key("damage_both_tools", "With two pickaxes, damage both. Off: only the active one.");
        key("stop_at_soft_block", "Stop climbing when your hands reach a soft block, so you cannot climb through dirt or leaves.");

        units(" blocks/tick", "speed");

        section("shimmy", "Shimmy", "Move sideways along a wall while hanging (hold A / D).");
        key("enabled", "Allow shimmying.");
        key("requires_dual", "Require a pickaxe in both hands.");
        key("speed", "Sideways speed in blocks per tick.");
        key("durability_cost", "Extra durability drained per second while shimmying, on top of the hang drain.");
        units(" blocks/tick", "speed");

        section("launch", "Launch", "Keep holding Space under a short soft cap on top of the rock to spring up and over it.");
        key("enabled", "Allow the climb-over-soft-cap launch.");
        key("charge_time", "Seconds you must hold Space against the cap before launching. Prevents accidental launches.");
        key("upward_boost", "Launch power when auto_height is off. 0.75 clears about 2 blocks.");
        key("max_cap_height", "Tallest soft cap you can launch over, in blocks. It needs standable ground on top.");
        key("auto_height", "Size the launch to land you on top of the cap. Off: always use upward_boost.");
        key("assist_speed", "Gentle push toward the wall while you rise so you land on the cap. 0 disables it.");

        units(" s", "charge_time");
        units(" blocks", "max_cap_height");

        section("wall_jump", "Wall Jump", "Tap crouch to release, then again within the window to jump off the wall.");
        key("time_window", "Seconds you have to double-tap crouch.");
        key("forward_boost", "How hard you are thrown away from the wall. Lower it if other mods react badly to the speed.");
        key("upward_boost", "How hard you are thrown upward.");

        units(" s", "time_window");

        section("enchantments", "Enchantments", "Per-level strength of the Gouge enchantments.");
        key("spring_bonus", "Extra launch and wall-jump power per Spring level. 0.15 is +15%.");
        key("spring_cap_bonus", "Extra soft-cap blocks you can launch over per Spring level, on top of launch.max_cap_height.");
        key("shimmy_bonus", "Extra shimmy speed per Shimmy level. 0.5 is +50%.");
        key("grip_bonus", "Extra seconds of hang time per Grip level.");
        key("momentum_reduction", "Seconds removed from the slip cooldown per Momentum level.");

        units(" s", "grip_bonus", "momentum_reduction");

        section("effects", "Effects", "Particles and sounds. Gameplay is not affected.");
        key("particles", "Show chip and dust particles.");
        key("sounds", "Play grip, scrape and launch sounds.");
        key("volume", "Volume multiplier for Gouge sounds.");

        units("x", "volume");

        section("hud", "HUD", "The grip stamina wheel beside the crosshair. Client-side, only drawn without a stamina mod.");
        key("enabled", "Show the wheel.");
        key("show_below", "Only show the wheel while stamina is below this fraction. 1 shows it whenever you grip or refill, 0.25 only when low.");
        key("left_side", "Draw the wheel left of the crosshair instead of right.");
        key("offset_x", "Move the wheel horizontally in GUI pixels (positive is right).");
        key("offset_y", "Move the wheel vertically in GUI pixels (positive is down).");
        key("scale", "Whole-number size multiplier.");
        key("opacity", "Overall opacity.");
        key("show_pips", "Show one gold pip per held pickaxe under the wheel.");
        key("show_rings", "Show one extra ring per Grip level. Off: always a single ring.");
        key("blink_when_low", "Blink when stamina is low and shake when it runs out.");
        key("hold_time", "Seconds the wheel stays on screen after it is no longer needed.");
        key("mid_threshold", "Below this fraction the wheel switches to the mid colour.");
        key("low_threshold", "Below this fraction the wheel switches to the low colour and blinks.");
        key("high_color", "Wheel colour while stamina is high, as #RRGGBB.");
        key("mid_color", "Wheel colour while stamina is getting low, as #RRGGBB.");
        key("low_color", "Wheel colour while stamina is nearly gone, as #RRGGBB.");
        group("Wheel", "enabled", "show_below", "hold_time", "show_pips", "show_rings", "blink_when_low");
        group("Position", "left_side", "offset_x", "offset_y", "scale", "opacity");
        group("Colors", "mid_threshold", "low_threshold", "high_color", "mid_color", "low_color");
        units(" s", "hold_time");
    }


    private static void group(String name, String... keys) {
        for (String k : keys) {
            GROUPS.put(current + "." + k, name);
        }
    }

    private static void units(String unit, String... keys) {
        for (String k : keys) {
            UNITS.put(current + "." + k, unit);
        }
    }

    public static String group(String section, String field) {
        return GROUPS.get(section + "." + field);
    }

    public static String unit(String section, String field) {
        return UNITS.getOrDefault(section + "." + field, "");
    }

    private static void section(String name, String title, String text) {
        current = name;
        SECTIONS.put(name, new String[]{title, text});
    }

    private static void key(String name, String text) {
        KEYS.put(current + "." + name, text);
    }

    private ConfigDocs() {
    }

    public static String title(String section) {
        return SECTIONS.get(section)[0];
    }

    public static String label(String field) {
        String spaced = field.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    public static String describe(String section, Field f, Object defaults) {
        String text = KEYS.get(section + "." + f.getName());
        if (text == null) {
            return "";
        }
        try {
            double[] range = ConfigRanges.of(section, f.getName());
            if (range != null) {
                text += " (" + num(range[0]) + " to " + num(range[1]) + ", default " + num(((Number) f.get(defaults)).doubleValue()) + ")";
            } else if (f.getType() == boolean.class) {
                text += " (default " + (f.getBoolean(defaults) ? "on" : "off") + ")";
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return text;
    }

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
