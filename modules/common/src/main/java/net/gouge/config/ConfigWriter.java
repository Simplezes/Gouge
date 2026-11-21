package net.gouge.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.lang.reflect.Field;
import java.util.List;

public final class ConfigWriter {

    private static final String RULE = "=======================================================";

    static void write(CommentedConfig c, ConfigData d) {
        ConfigData defaults = new ConfigData();
        boolean first = true;
        for (String name : ConfigRanges.SECTIONS) {
            Object t = ConfigRanges.section(d, name);
            Object def = ConfigRanges.section(defaults, name);
            String lastGroup = null;
            for (Field f : t.getClass().getFields()) {
                String path = name + "." + f.getName();
                try {
                    c.set(path, f.get(t));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
                String group = ConfigDocs.group(name, f.getName());
                String banner = group != null && !group.equals(lastGroup) ? " -- " + group + " --\n" : "";
                lastGroup = group;
                c.setComment(path, banner + " " + ConfigDocs.describe(name, f, def));
            }
            String[] info = ConfigDocs.SECTIONS.get(name);
            String head = " " + RULE + "\n " + info[0].toUpperCase() + "\n " + info[1];
            if (first) {
                head = " " + RULE + "\n GOUGE CONFIGURATION\n Easiest: edit it in-game (Mods list, needs Cloth Config). By hand: save, then '/gouge reload'.\n"
                        + " Out-of-range values are clamped on load. Pickaxe stats are at the bottom.\n"
                        + " Optional tables you can add (or edit in-game under Blocks):\n"
                        + "   [block_overrides]  \"minecraft:sandstone\" = \"hard\" or \"soft\" (default: tool-required vanilla blocks and modded blocks are hard)\n"
                        + "   [block_grip]       \"minecraft:ice\" = 0.25 or \"#minecraft:logs\" = 1.5 (hang time multiplier, 0 to 10)\n" + head;
                first = false;
            }
            c.setComment(name, head + "\n " + RULE);
        }

        c.setComment("pickaxes", """
                 =======================================================
                 PICKAXES
                 How long each pickaxe lets you hang and how fast it wears.
                 Keys: an item ("minecraft:diamond_pickaxe"), a tag ("#c:pickaxes") or "fallback".
                 Priority: item, then tag, then fallback.
                 hang_time: seconds before slipping (0 - 3600).
                 damage_rate: durability lost per second while hanging or sliding (0 - 1000).
                 =======================================================""");
        for (var entry : d.pickaxes.entrySet()) {
            c.set(List.of("pickaxes", entry.getKey(), "hang_time"), entry.getValue().hang_time);
            c.set(List.of("pickaxes", entry.getKey(), "damage_rate"), entry.getValue().damage_rate);
        }

        if (!d.block_overrides.isEmpty()) {
            c.set("block_overrides", CommentedConfig.inMemory());
            c.setComment("block_overrides", " Blocks that hang (\"hard\") or slide (\"soft\") differently from the default.");
            for (var entry : d.block_overrides.entrySet()) {
                c.set(List.of("block_overrides", entry.getKey()), entry.getValue());
            }
        }
        if (!d.block_grip.isEmpty()) {
            c.set("block_grip", CommentedConfig.inMemory());
            c.setComment("block_grip", " Hang time multiplier per block or #tag (0 to 10). 1 is normal, 0.5 slips twice as fast.");
            for (var entry : d.block_grip.entrySet()) {
                c.set(List.of("block_grip", entry.getKey()), entry.getValue());
            }
        }
    }
}
