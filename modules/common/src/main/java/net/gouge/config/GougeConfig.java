package net.gouge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.gouge.physics.Pickaxes;
import net.gouge.platform.GougePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.gouge.config.ConfigReader.*;
import static net.gouge.config.ConfigValidator.*;
import static net.gouge.config.ConfigWriter.*;

public class GougeConfig {

    public static final Logger LOGGER = LoggerFactory.getLogger("gouge");
    public static ConfigData INSTANCE = new ConfigData();

    public static void load() {
        Path configDir = GougePlatform.get().configDir();
        Path configFile = configDir.resolve("gouge.toml");
        boolean isNew = !Files.exists(configFile);

        CommentedFileConfig config = CommentedFileConfig.builder(configFile, TomlFormat.instance())
                .preserveInsertionOrder()
                .sync()
                .build();
        try {
            config.load();
        } catch (Exception e) {
            LOGGER.error("Could not read gouge.toml, keeping previous settings", e);
        }

        INSTANCE = validate(read(config));

        if (isNew || hasMissingKeys(config, INSTANCE)) {
            write(config, INSTANCE);
            config.save();
        }
        config.close();
        Pickaxes.invalidateCache();
    }

    static boolean hasMissingKeys(CommentedConfig existing, ConfigData data) {
        CommentedConfig fresh = CommentedConfig.inMemory();
        write(fresh, data);
        return missing(fresh, existing, List.of());
    }

    static boolean missing(CommentedConfig fresh, CommentedConfig existing, List<String> parent) {
        for (var entry : fresh.entrySet()) {
            List<String> path = new java.util.ArrayList<>(parent);
            path.add(entry.getKey());
            if (!existing.contains(path)) {
                return true;
            }
            if (entry.getValue() instanceof CommentedConfig child && missing(child, existing, path)) {
                return true;
            }
        }
        return false;
    }

    public static void save() {
        Path file = GougePlatform.get().configDir().resolve("gouge.toml");
        CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance())
                .preserveInsertionOrder()
                .sync()
                .build();
        try {
            write(config, validate(INSTANCE));
            config.save();
        } finally {
            config.close();
        }
        Pickaxes.invalidateCache();
    }
}
