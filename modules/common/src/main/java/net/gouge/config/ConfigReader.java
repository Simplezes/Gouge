package net.gouge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.gouge.config.ConfigData.PickaxeStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigReader {

    static double num(CommentedConfig c, String path, double def) {
        Object v = c.get(path);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    static int inum(CommentedConfig c, String path, int def) {
        Object v = c.get(path);
        return (v instanceof Number n) ? n.intValue() : def;
    }

    static boolean bool(CommentedConfig c, String path, boolean def) {
        Object v = c.get(path);
        return (v instanceof Boolean b) ? b : def;
    }

    static ConfigData read(CommentedConfig c) {
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
}
