package net.gouge;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import net.gouge.config.ConfigData;
import net.gouge.config.ConfigData.PickaxeStats;
import net.gouge.config.ConfigDocs;
import net.gouge.config.ConfigRanges;
import net.gouge.config.GougeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GougeConfigScreen {
    private BooleanListEntry staminaToggle;
    private BooleanListEntry hudToggle;

    private static final Map<String, List<String>> TABS = new LinkedHashMap<>();
    private static final Map<String, String> TAB_INFO = new LinkedHashMap<>();

    static {
        TABS.put("Grip", List.of("grip", "slide"));
        TABS.put("Movement", List.of("climbing", "shimmy", "launch", "wall_jump"));
        TABS.put("Stamina (Experimental)", List.of("stamina"));
        TABS.put("Enchantments", List.of("enchantments"));
        TABS.put("Interface", List.of("effects", "hud"));
        TAB_INFO.put("Grip", "Grabbing a wall with a pickaxe, how long you hold on, and sliding down soft blocks.");
        TAB_INFO.put("Movement", "What you can do while gripping: climb, shimmy sideways, launch over soft caps, wall jump.");
        TAB_INFO.put("Enchantments", "How strong each level of the Gouge enchantments is.");
        TAB_INFO.put("Interface", "Particles, sounds and the stamina wheel. None of this changes gameplay.");
    }

    private final ConfigEntryBuilder entries;
    private final Screen[] built = new Screen[1];

    private GougeConfigScreen(ConfigEntryBuilder entries) {
        this.entries = entries;
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Gouge"))
                .setAlwaysShowTabs(true)
                .setSavingRunnable(GougeConfig::save);
        GougeConfigScreen screen = new GougeConfigScreen(builder.entryBuilder());
        ConfigData current = GougeConfig.INSTANCE;
        ConfigData defaults = new ConfigData();
        for (var tab : TABS.entrySet()) {
            ConfigCategory category = builder.getOrCreateCategory(Component.literal(tab.getKey()));
            String info = TAB_INFO.get(tab.getKey());
            if (info == null) {
                info = ConfigDocs.SECTIONS.get(tab.getValue().get(0))[1];
                screen.intro(category, Component.literal(info).withStyle(ChatFormatting.GOLD));
            } else {
                screen.intro(category, Component.literal(info));
            }
            for (String name : tab.getValue()) {
                screen.section(category, name, ConfigRanges.section(current, name), ConfigRanges.section(defaults, name));
            }
            if (tab.getKey().equals("Grip")) {
                screen.lists(category, current);
            }
        }
        screen.built[0] = builder.build();
        return screen.built[0];
    }

    private void intro(ConfigCategory category, Component text) {
        category.addEntry(entries.startTextDescription(text).build());
    }

    private void section(ConfigCategory category, String name, Object target, Object defaults) {
        List<AbstractConfigListEntry> pending = new ArrayList<>();
        String title = null;
        for (Field f : target.getClass().getFields()) {
            String group = ConfigDocs.group(name, f.getName());
            String next = group != null ? group : ConfigDocs.title(name);
            if (title != null && !title.equals(next)) {
                flush(category, title, pending);
            }
            title = next;
            try {
                AbstractConfigListEntry entry = entry(name, f, target, defaults);
                if (entry instanceof BooleanListEntry toggle && f.getName().equals("enabled")) {
                    if (name.equals("stamina")) {
                        staminaToggle = toggle;
                    } else if (name.equals("hud")) {
                        hudToggle = toggle;
                    }
                }
                if (entry != null) {
                    pending.add(entry);
                }
            } catch (IllegalAccessException e) {
                GougeConfig.LOGGER.warn("Skipping config field {}.{}", name, f.getName(), e);
            }
        }
        flush(category, title, pending);
    }

    private void flush(ConfigCategory category, String title, List<AbstractConfigListEntry> pending) {
        category.addEntry(entries.startTextDescription(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        pending.forEach(category::addEntry);
        pending.clear();
    }

    private AbstractConfigListEntry entry(String section, Field f, Object target, Object defaults) throws IllegalAccessException {
        Component label = Component.literal(ConfigDocs.label(f.getName()));
        Component tip = Component.literal(ConfigDocs.describe(section, f, defaults));
        String unit = ConfigDocs.unit(section, f.getName());
        double[] range = ConfigRanges.of(section, f.getName());
        Requirement req = requirement(section, f.getName());
        if (f.getType() == boolean.class) {
            var b = entries.startBooleanToggle(label, f.getBoolean(target))
                    .setDefaultValue(f.getBoolean(defaults))
                    .setTooltip(tip)
                    .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, v));
            return b.build();
        }
        if (f.getType() == int.class) {
            int value = f.getInt(target);
            int def = f.getInt(defaults);
            if (range[1] - range[0] <= 200) {
                var s = entries.startIntSlider(label, value, (int) range[0], (int) range[1])
                        .setDefaultValue(def)
                        .setTooltip(tip)
                        .setTextGetter(v -> Component.literal(v + unit))
                        .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, v));
                return s.build();
            }
            var n = entries.startIntField(label, value)
                    .setDefaultValue(def)
                    .setTooltip(tip)
                    .setMin((int) range[0]).setMax((int) range[1])
                    .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, v));
            return n.build();
        }
        if (f.getType() == double.class) {
            double value = f.getDouble(target);
            double def = f.getDouble(defaults);
            double step = step(range[1] - range[0]);
            if (step > 0 && onStep(value, step) && onStep(def, step)) {
                var s = entries.startIntSlider(label, ticks(value, step), (int) Math.ceil(range[0] / step - 1e-9),
                                (int) Math.floor(range[1] / step + 1e-9))
                        .setDefaultValue(ticks(def, step))
                        .setTooltip(tip)
                        .setTextGetter(v -> Component.literal(fmt(v * step) + unit))
                        .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, Math.round(v * step * 1e6) / 1e6));
                return s.build();
            }
            var n = entries.startDoubleField(label, value)
                    .setDefaultValue(def)
                    .setTooltip(tip)
                    .setMin(range[0]).setMax(range[1])
                    .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, v));
            return n.build();
        }
        if (f.getType() == String.class) {
            String value = (String) f.get(target);
            String def = (String) f.get(defaults);
            if (f.getName().endsWith("_color")) {
                var c = new ColorFieldBuilder(entries.getResetButtonKey(), label, parseColor(value, parseColor(def, 0xFFFFFF)))
                        .setDefaultValue(parseColor(def, 0xFFFFFF))
                        .setTooltip(tip)
                        .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, String.format(Locale.ROOT, "#%06X", v & 0xFFFFFF)));
                return c.build();
            }
            var t = entries.startStrField(label, value).setDefaultValue(def).setTooltip(tip)
                    .setRequirement(req)
                    .setSaveConsumer(v -> set(f, target, v));
            return t.build();
        }
        return null;
    }

    private Requirement requirement(String section, String field) {
        boolean toggle = field.equals("enabled");
        if (section.equals("stamina") && !toggle && staminaToggle != null) {
            return Requirement.isTrue(staminaToggle);
        }
        if (section.equals("hud") && staminaToggle != null && hudToggle != null && !toggle) {
            return Requirement.all(Requirement.isTrue(staminaToggle), Requirement.isTrue(hudToggle));
        }
        if (section.equals("hud") && staminaToggle != null && toggle) {
            return Requirement.isTrue(staminaToggle);
        }
        return null;
    }

    private static double step(double span) {
        return span <= 1 ? 0.01 : span <= 10 ? 0.05 : span <= 40 ? 0.1 : span <= 200 ? 1 : 0;
    }

    private static boolean onStep(double v, double step) {
        double q = v / step;
        return Math.abs(q - Math.round(q)) < 1e-6;
    }

    private static int ticks(double v, double step) {
        return (int) Math.round(v / step);
    }

    private static String fmt(double v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static int parseColor(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static void set(Field f, Object target, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            GougeConfig.LOGGER.warn("Could not set config field {}", f.getName(), e);
        }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("fallback|#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static double parse(String s, double fallback, double min, double max) {
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isNaN(v) ? fallback : Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void opener(ConfigCategory category, String label, String button, RowsScreen.Spec spec,
                        Supplier<List<String[]>> load, Consumer<List<String[]>> apply) {
        category.addEntry(new ButtonEntry(Component.literal(label), Component.literal(button), () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new RowsScreen(built[0], spec, load.get(), rows -> {
                apply.accept(rows);
                GougeConfig.save();
            }));
        }));
    }

    private void lists(ConfigCategory category, ConfigData current) {
        category.addEntry(entries.startTextDescription(Component.literal("Pickaxes and blocks").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        intro(category, Component.literal("Per-item and per-block settings. Each button opens a list where you can add and remove "
                + "entries; they are saved as soon as you press Done there."));
        var pickaxes = new RowsScreen.Spec("Pickaxes",
                "How long each pickaxe lets you hang (0-3600 s) and how fast it wears (0-1000 durability/s). Priority: item, then #tag, then fallback.",
                new String[]{"Item, #tag or fallback", "Hang time (s)", "Wear / s"}, false,
                new String[]{"minecraft:iron_pickaxe", "3.0", "2"}, false);
        opener(category, "Pickaxes", "Pickaxes: hang time and wear...", pickaxes, () -> {
            List<String[]> rows = new ArrayList<>();
            current.pickaxes.forEach((id, st) -> rows.add(new String[]{id, fmt(st.hang_time), String.valueOf(st.damage_rate)}));
            return rows;
        }, rows -> {
            Map<String, PickaxeStats> map = new LinkedHashMap<>();
            rows.stream().filter(r -> validId(r[0])).forEach(r -> map.put(r[0],
                    new PickaxeStats(parse(r[1], 3.0, 0, 3600), (int) parse(r[2], 2, 0, 1000))));
            current.pickaxes = map;
        });
        var blocks = new RowsScreen.Spec("Blocks: hard or soft, and grip strength",
                "Hard blocks hold you, soft blocks make you slide. Grip is a hang time multiplier (0-10): 1 is normal, 0.5 slips twice as fast, "
                        + "2 holds twice as long. Tags (#...) only support grip. Entries left on Default and 1 have no effect and are not saved.",
                new String[]{"Block or #tag", "Behaves as", "Grip x"}, true, new String[]{"minecraft:", "default", "1.0"}, true);
        opener(category, "Blocks", "Blocks: hard or soft, and grip...", blocks, () -> {
            java.util.Set<String> ids = new java.util.LinkedHashSet<>(current.block_overrides.keySet());
            ids.addAll(current.block_grip.keySet());
            List<String[]> rows = new ArrayList<>();
            ids.forEach(id -> rows.add(new String[]{id, current.block_overrides.getOrDefault(id, "default"),
                    fmt(current.block_grip.getOrDefault(id, 1.0))}));
            return rows;
        }, rows -> {
            Map<String, String> kinds = new LinkedHashMap<>();
            Map<String, Double> grips = new LinkedHashMap<>();
            for (String[] r : rows) {
                if (!validId(r[0])) {
                    continue;
                }
                if (!r[1].equals("default") && !r[0].startsWith("#")) {
                    kinds.put(r[0], r[1]);
                }
                double grip = parse(r[2], 1.0, 0, 10);
                if (grip != 1.0) {
                    grips.put(r[0], grip);
                }
            }
            current.block_overrides = kinds;
            current.block_grip = grips;
        });
    }
}
