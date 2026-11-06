package net.gouge;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Locale;

public final class GougeConfigScreen {

    private GougeConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Gouge"))
                .setSavingRunnable(GougeConfig::save);
        ConfigEntryBuilder entries = builder.entryBuilder();
        GougeConfig.ConfigData current = GougeConfig.INSTANCE;
        GougeConfig.ConfigData defaults = new GougeConfig.ConfigData();
        for (String name : ConfigRanges.SECTIONS) {
            section(builder, entries, name, read(current, name), read(defaults, name));
        }
        return builder.build();
    }

    private static Object read(GougeConfig.ConfigData data, String name) {
        try {
            return GougeConfig.ConfigData.class.getField(name).get(data);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pretty(String key) {
        String spaced = key.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static void section(ConfigBuilder builder, ConfigEntryBuilder entries, String name, Object target, Object defaults) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal(pretty(name)));
        for (Field f : target.getClass().getFields()) {
            try {
                Component label = Component.literal(pretty(f.getName()));
                double[] range = ConfigRanges.of(name, f.getName());
                if (f.getType() == boolean.class) {
                    category.addEntry(entries.startBooleanToggle(label, f.getBoolean(target))
                            .setDefaultValue(f.getBoolean(defaults))
                            .setSaveConsumer(v -> set(f, target, v))
                            .build());
                } else if (f.getType() == int.class) {
                    var entry = entries.startIntField(label, f.getInt(target))
                            .setDefaultValue(f.getInt(defaults))
                            .setSaveConsumer(v -> set(f, target, v));
                    if (range != null) {
                        entry.setMin((int) range[0]).setMax((int) range[1]);
                    }
                    category.addEntry(entry.build());
                } else if (f.getType() == double.class) {
                    var entry = entries.startDoubleField(label, f.getDouble(target))
                            .setDefaultValue(f.getDouble(defaults))
                            .setSaveConsumer(v -> set(f, target, v));
                    if (range != null) {
                        entry.setMin(range[0]).setMax(range[1]);
                    }
                    category.addEntry(entry.build());
                }
            } catch (IllegalAccessException e) {
                GougeConfig.LOGGER.warn("Skipping config field {}.{}", name, f.getName(), e);
            }
        }
    }

    private static void set(Field f, Object target, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            GougeConfig.LOGGER.warn("Could not set config field {}", f.getName(), e);
        }
    }
}
