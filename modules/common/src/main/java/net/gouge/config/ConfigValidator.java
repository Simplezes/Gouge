package net.gouge.config;

import net.gouge.config.ConfigData.PickaxeStats;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.gouge.config.GougeConfig.*;

public final class ConfigValidator {

    static ConfigData validate(ConfigData c) {
        ConfigData defaults = new ConfigData();
        try {
            for (String name : ConfigRanges.SECTIONS) {
                Field holder = ConfigData.class.getField(name);
                if (holder.get(c) == null) {
                    holder.set(c, holder.get(defaults));
                }
                Object t = holder.get(c);
                for (Field f : t.getClass().getFields()) {
                    double[] r = ConfigRanges.of(name, f.getName());
                    if (r == null) {
                        continue;
                    }
                    String path = name + "." + f.getName();
                    if (f.getType() == int.class) {
                        f.setInt(t, (int) clamp(path, f.getInt(t), r[0], r[1]));
                    } else if (f.getType() == double.class) {
                        f.setDouble(t, clamp(path, f.getDouble(t), r[0], r[1]));
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        c.hud.low_threshold = clamp("hud.low_threshold", c.hud.low_threshold, 0.0, c.hud.mid_threshold);
        if (c.block_grip == null) {
            c.block_grip = new LinkedHashMap<>();
        }
        if (c.pickaxes == null) {
            c.pickaxes = defaults.pickaxes;
        }
        if (c.pickaxe_blacklist == null) {
            c.pickaxe_blacklist = new java.util.ArrayList<>();
        }
        c.pickaxe_blacklist.removeIf(id -> {
            boolean bad = id == null || !id.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
            if (bad) {
                LOGGER.warn("Ignoring invalid pickaxe_blacklist entry in gouge.toml: {}", id);
            }
            return bad;
        });
        if (c.block_overrides == null) {
            c.block_overrides = new LinkedHashMap<>();
        }
        c.block_grip.replaceAll((k, v) -> clamp("block_grip." + k, v, 0.0, 10.0));

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
