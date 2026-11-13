package net.gouge.config;

import net.gouge.config.ConfigData.Climbing;
import net.gouge.config.ConfigData.Effects;
import net.gouge.config.ConfigData.Enchantments;
import net.gouge.config.ConfigData.Launch;
import net.gouge.config.ConfigData.Mechanics;
import net.gouge.config.ConfigData.PickaxeStats;
import net.gouge.config.ConfigData.Traverse;
import net.gouge.config.ConfigData.WallJump;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.gouge.config.GougeConfig.*;

public final class ConfigValidator {

    static ConfigData validate(ConfigData c) {
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

    static double clamp(String name, double value, double min, double max) {
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
}
