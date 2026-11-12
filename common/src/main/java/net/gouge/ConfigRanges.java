package net.gouge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigRanges {

    public static final List<String> SECTIONS = List.of("mechanics", "climbing", "traverse", "launch", "wall_jump",
            "enchantments", "effects");

    public static final Map<String, double[]> RANGES = new LinkedHashMap<>();

    static {
        RANGES.put("mechanics.reach", new double[]{0.5, 32});
        RANGES.put("mechanics.slip_cooldown", new double[]{0, 600});
        RANGES.put("mechanics.soft_fall_damage", new double[]{0, 1});
        RANGES.put("mechanics.soft_fall_damage_cap", new double[]{0, 40});
        RANGES.put("mechanics.trail_length", new double[]{0, 16});
        RANGES.put("mechanics.min_fall_distance", new double[]{0, 32});
        RANGES.put("mechanics.max_drift", new double[]{0, 16});
        RANGES.put("mechanics.dual_grip_multiplier", new double[]{1, 10});
        RANGES.put("mechanics.release_cooldown", new double[]{0, 600});
        RANGES.put("mechanics.stamina_hang_cost", new double[]{0, 100});
        RANGES.put("mechanics.stamina_climb_cost", new double[]{0, 100});
        RANGES.put("mechanics.stamina_shimmy_cost", new double[]{0, 100});
        RANGES.put("mechanics.soft_slide_ramp", new double[]{0, 1});
        RANGES.put("climbing.speed", new double[]{0.01, 0.5});
        RANGES.put("climbing.reach", new double[]{0.5, 32});
        RANGES.put("climbing.durability_cost", new double[]{0, 100});
        RANGES.put("traverse.speed", new double[]{0.01, 0.3});
        RANGES.put("traverse.durability_cost", new double[]{0, 100});
        RANGES.put("launch.charge_time", new double[]{0, 5});
        RANGES.put("launch.upward_boost", new double[]{0.1, 2});
        RANGES.put("launch.max_cap_height", new double[]{1, 4});
        RANGES.put("launch.assist_speed", new double[]{0, 0.5});
        RANGES.put("wall_jump.time_window", new double[]{0.05, 5});
        RANGES.put("wall_jump.forward_boost", new double[]{0, 5});
        RANGES.put("wall_jump.upward_boost", new double[]{0, 5});
        RANGES.put("enchantments.spring_bonus", new double[]{0, 2});
        RANGES.put("enchantments.traverse_bonus", new double[]{0, 5});
        RANGES.put("enchantments.grip_bonus", new double[]{0, 60});
        RANGES.put("enchantments.momentum_reduction", new double[]{0, 60});
        RANGES.put("effects.volume", new double[]{0, 2});
    }

    private ConfigRanges() {
    }

    public static double[] of(String section, String field) {
        return RANGES.get(section + "." + field);
    }
}
