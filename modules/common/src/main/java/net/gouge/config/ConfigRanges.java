package net.gouge.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigRanges {

    public static final List<String> SECTIONS = List.of("grip", "slide", "stamina", "climbing", "shimmy", "launch",
            "wall_jump", "enchantments", "effects", "hud");

    public static final Map<String, double[]> RANGES = new LinkedHashMap<>();

    static {
        RANGES.put("grip.reach", new double[]{0.5, 32});
        RANGES.put("grip.slip_cooldown", new double[]{0, 600});
        RANGES.put("slide.fall_damage", new double[]{0, 1});
        RANGES.put("slide.fall_damage_cap", new double[]{0, 40});
        RANGES.put("slide.trail_length", new double[]{0, 16});
        RANGES.put("grip.min_fall_distance", new double[]{0, 32});
        RANGES.put("grip.max_drift", new double[]{0, 16});
        RANGES.put("grip.dual_multiplier", new double[]{1, 10});
        RANGES.put("grip.release_cooldown", new double[]{0, 600});
        RANGES.put("stamina.refill_time", new double[]{0, 120});
        RANGES.put("stamina.hang_cost", new double[]{0, 100});
        RANGES.put("slide.ramp", new double[]{0, 1});
        RANGES.put("climbing.speed", new double[]{0.01, 0.5});
        RANGES.put("climbing.reach", new double[]{0.5, 32});
        RANGES.put("climbing.durability_cost", new double[]{0, 100});
        RANGES.put("shimmy.speed", new double[]{0.01, 0.3});
        RANGES.put("shimmy.durability_cost", new double[]{0, 100});
        RANGES.put("launch.charge_time", new double[]{0, 5});
        RANGES.put("launch.upward_boost", new double[]{0.1, 2});
        RANGES.put("launch.max_cap_height", new double[]{1, 4});
        RANGES.put("launch.assist_speed", new double[]{0, 0.5});
        RANGES.put("wall_jump.time_window", new double[]{0.05, 5});
        RANGES.put("wall_jump.forward_boost", new double[]{0, 5});
        RANGES.put("wall_jump.upward_boost", new double[]{0, 5});
        RANGES.put("enchantments.spring_bonus", new double[]{0, 2});
        RANGES.put("enchantments.spring_cap_bonus", new double[]{0, 4});
        RANGES.put("enchantments.shimmy_bonus", new double[]{0, 5});
        RANGES.put("enchantments.grip_bonus", new double[]{0, 60});
        RANGES.put("enchantments.momentum_reduction", new double[]{0, 60});
        RANGES.put("effects.volume", new double[]{0, 2});
        RANGES.put("hud.show_below", new double[]{0, 1});
        RANGES.put("hud.offset_x", new double[]{-400, 400});
        RANGES.put("hud.offset_y", new double[]{-400, 400});
        RANGES.put("hud.scale", new double[]{1, 4});
        RANGES.put("hud.opacity", new double[]{0.1, 1});
        RANGES.put("hud.hold_time", new double[]{0, 10});
        RANGES.put("hud.mid_threshold", new double[]{0, 1});
        RANGES.put("hud.low_threshold", new double[]{0, 1});
        RANGES.put("stamina.refill_delay", new double[]{0, 30});
        RANGES.put("stamina.climb_multiplier", new double[]{0, 10});
        RANGES.put("stamina.shimmy_multiplier", new double[]{0, 10});
    }

    private ConfigRanges() {
    }

    public static Object section(ConfigData d, String name) {
        try {
            return ConfigData.class.getField(name).get(d);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static double[] of(String section, String field) {
        return RANGES.get(section + "." + field);
    }
}
