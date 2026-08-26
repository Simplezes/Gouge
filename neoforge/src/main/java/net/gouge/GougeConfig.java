package net.gouge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class GougeConfig {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static ConfigData INSTANCE = new ConfigData();

    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        File configFile = configDir.resolve("gouge.jsonc").toFile();

        if (configFile.exists()) {
            try {
                String raw = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                String stripped = raw.replaceAll("//[^\n]*", "");
                INSTANCE = GSON.fromJson(stripped, ConfigData.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save(configFile);
        }
    }

    public static void save(File configFile) {
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(DEFAULT_CONFIG);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ConfigData {

        public Mechanics mechanics = new Mechanics();
        public WallJump wall_jump = new WallJump();
        public Enchantments enchantments = new Enchantments();
        public Map<String, PickaxeStats> pickaxes = new HashMap<>();

        public ConfigData() {
            pickaxes.put("minecraft:wooden_pickaxe", new PickaxeStats(2.0, 1));
            pickaxes.put("minecraft:stone_pickaxe", new PickaxeStats(4.0, 2));
            pickaxes.put("minecraft:golden_pickaxe", new PickaxeStats(1.5, 1));
            pickaxes.put("minecraft:iron_pickaxe", new PickaxeStats(6.0, 3));
            pickaxes.put("minecraft:diamond_pickaxe", new PickaxeStats(10.0, 5));
            pickaxes.put("minecraft:netherite_pickaxe", new PickaxeStats(15.0, 7));
            pickaxes.put("fallback", new PickaxeStats(3.0, 2));
        }
    }

    public static class Mechanics {

        public double reach = 2.5;
        public double slip_cooldown = 5.0;
        public double soft_fall_damage = 0.5;
        public int trail_length = 5;
    }

    public static class WallJump {

        public double time_window = 0.7;
        public double forward_boost = 1.4;
        public double upward_boost = 1.1;
    }

    public static class Enchantments {

        public double grip_bonus = 2.0;
        public double momentum_reduction = 1.0;
    }

    public static class PickaxeStats {

        public double hang_time;
        public int damage_rate;

        public PickaxeStats() {
        }

        public PickaxeStats(double hang_time, int damage_rate) {
            this.hang_time = hang_time;
            this.damage_rate = damage_rate;
        }
    }

    private static final String DEFAULT_CONFIG = """
{
  // =======================================================
  // GOUGE CONFIGURATION
  //
  // Tip: Save this file and run '/gouge reload' in-game!
  // =======================================================

  "mechanics": {
    // How far from a wall you can be and still grab onto it. Default is 2.5 blocks.
    // Increase this if grabbing feels too hard to trigger.
    "reach": 2.5,

    // After slipping off a wall, how long before you can grab again (in seconds).
    // Set to 0 to remove the cooldown entirely.
    "slip_cooldown": 5.0,

    // When you land on a soft block (dirt, grass, leaves, etc.) after sliding,
    // this multiplies the fall damage you take. 0.5 (50%) means half damage, 0.0 means none.
    "soft_fall_damage": 0.5,

    // How many blocks get the cracking visual effect as you slide down a wall.
    // Higher = longer crack trail behind you.
    "trail_length": 5
  },

  "wall_jump": {
    // How quickly you need to double-tap crouch to trigger the wall jump (in seconds).
    // Tap crouch once to release, then tap it again within this window to jump.
    "time_window": 0.7,

    // How hard you get launched away from the wall horizontally when wall jumping.
    // 1.4 is default. Lower this if ragdoll mods or other mods react badly to the speed change.
    "forward_boost": 1.4,

    // How hard you get launched upward when wall jumping.
    // 1.1 is default. This is what gives you height on the jump.
    "upward_boost": 1.1
  },

  "enchantments": {
    // How many extra seconds of hang time you get per level of the Grip enchantment.
    // Grip II with 2.0 here gives you 4 extra seconds before slipping.
    "grip_bonus": 2.0,

    // How many seconds are removed from your slip cooldown per level of the Momentum enchantment.
    // Momentum III with 1.0 here cuts 3 seconds off your cooldown.
    "momentum_reduction": 1.0
  },

  // =======================================================
  // PICKAXE STATS
  // Controls how long each pickaxe lets you hang and how fast it breaks.
  //
  // Keys can be:
  //   - A specific item:  "minecraft:diamond_pickaxe"
  //   - An item tag:      "#c:pickaxes"  (covers all pickaxes from that tag)
  //   - "fallback"        used if nothing else matches
  //
  // Priority order: Specific Item > Tag > fallback
  // =======================================================
  "pickaxes": {
    "minecraft:wooden_pickaxe": {
      // How long you can hang before slipping (seconds)
      "hang_time": 2.0,
      // How many durability points are drained per second while hanging
      "damage_rate": 1
    },
    "minecraft:stone_pickaxe": {
      "hang_time": 4.0,
      "damage_rate": 2
    },
    "minecraft:golden_pickaxe": {
      "hang_time": 1.5,
      "damage_rate": 1
    },
    "minecraft:iron_pickaxe": {
      "hang_time": 6.0,
      "damage_rate": 3
    },
    "minecraft:diamond_pickaxe": {
      "hang_time": 10.0,
      "damage_rate": 5
    },
    "minecraft:netherite_pickaxe": {
      "hang_time": 15.0,
      "damage_rate": 7
    },
    "fallback": {
      "hang_time": 3.0,
      "damage_rate": 2
    }
  }
}
""";
}
