package net.gouge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.gouge.config.ConfigData.PickaxeStats;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

    static String str(CommentedConfig c, String path, String def) {
        Object v = c.get(path);
        return (v instanceof String s) ? s : def;
    }

    static boolean bool(CommentedConfig c, String path, boolean def) {
        Object v = c.get(path);
        return (v instanceof Boolean b) ? b : def;
    }

    private static final String[] LEGACY = {
            "mechanics.reach=grip.reach", "mechanics.min_fall_distance=grip.min_fall_distance",
            "mechanics.max_drift=grip.max_drift", "mechanics.slip_cooldown=grip.slip_cooldown",
            "mechanics.release_cooldown=grip.release_cooldown", "mechanics.unlimited_grip=grip.unlimited",
            "mechanics.cling_everything=grip.cling_everything", "mechanics.block_other_hand=grip.block_other_hand",
            "mechanics.client_prediction=grip.client_prediction", "mechanics.dual_grip_enabled=grip.dual_enabled",
            "mechanics.dual_grip_multiplier=grip.dual_multiplier", "mechanics.soft_fall_damage=slide.fall_damage",
            "mechanics.soft_fall_damage_cap=slide.fall_damage_cap", "mechanics.trail_length=slide.trail_length",
            "mechanics.soft_slide_ramp=slide.ramp", "experimental.persistent_stamina=stamina.enabled",
            "mechanics.use_mod_stamina=stamina.use_mod", "mechanics.stamina_hang_cost=stamina.hang_cost",
            "mechanics.stamina_climb_multiplier=stamina.climb_multiplier",
            "mechanics.stamina_shimmy_multiplier=stamina.shimmy_multiplier",
            "mechanics.stamina_refill_time=stamina.refill_time", "mechanics.stamina_refill_delay=stamina.refill_delay",
            "traverse.enabled=shimmy.enabled", "traverse.requires_dual=shimmy.requires_dual",
            "traverse.speed=shimmy.speed", "traverse.durability_cost=shimmy.durability_cost",
            "enchantments.traverse_bonus=enchantments.shimmy_bonus"
    };

    static boolean migrate(CommentedConfig c) {
        boolean found = false;
        for (String pair : LEGACY) {
            String[] p = pair.split("=");
            if (c.contains(p[0])) {
                found = true;
                if (!c.contains(p[1])) {
                    c.set(p[1], c.get(p[0]));
                }
            }
        }
        return found;
    }

    static ConfigData read(CommentedConfig c) {
        migrate(c);
        ConfigData d = new ConfigData();
        for (String name : ConfigRanges.SECTIONS) {
            Object t = ConfigRanges.section(d, name);
            for (Field f : t.getClass().getFields()) {
                String path = name + "." + f.getName();
                try {
                    if (f.getType() == boolean.class) {
                        f.setBoolean(t, bool(c, path, f.getBoolean(t)));
                    } else if (f.getType() == int.class) {
                        f.setInt(t, inum(c, path, f.getInt(t)));
                    } else if (f.getType() == double.class) {
                        f.setDouble(t, num(c, path, f.getDouble(t)));
                    } else if (f.getType() == String.class) {
                        f.set(t, str(c, path, (String) f.get(t)));
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

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

        Object blacklistRaw = c.get(List.of("pickaxe_blacklist"));
        if (blacklistRaw instanceof List<?> list) {
            List<String> parsed = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s) {
                    parsed.add(s);
                }
            }
            d.pickaxe_blacklist = parsed;
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
