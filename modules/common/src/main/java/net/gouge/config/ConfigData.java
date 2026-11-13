package net.gouge.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigData {
    public Mechanics mechanics = new Mechanics();
    public WallJump wall_jump = new WallJump();
    public Enchantments enchantments = new Enchantments();
    public Climbing climbing = new Climbing();
    public Traverse traverse = new Traverse();
    public Launch launch = new Launch();
    public Effects effects = new Effects();
    public Map<String, Double> block_grip = new LinkedHashMap<>();
    public Map<String, PickaxeStats> pickaxes = new LinkedHashMap<>();
    public Map<String, String> block_overrides = new LinkedHashMap<>();

    public ConfigData() {
        pickaxes.put("minecraft:wooden_pickaxe", new PickaxeStats(2.0, 1));
        pickaxes.put("minecraft:stone_pickaxe", new PickaxeStats(4.0, 2));
        pickaxes.put("minecraft:golden_pickaxe", new PickaxeStats(1.5, 1));
        pickaxes.put("minecraft:iron_pickaxe", new PickaxeStats(6.0, 3));
        pickaxes.put("minecraft:diamond_pickaxe", new PickaxeStats(10.0, 5));
        pickaxes.put("minecraft:netherite_pickaxe", new PickaxeStats(15.0, 7));
        pickaxes.put("fallback", new PickaxeStats(3.0, 2));
    }

    public static class Mechanics {

        public double reach = 2.5;
        public double slip_cooldown = 5.0;
        public double soft_fall_damage = 0.5;
        public double soft_fall_damage_cap = 6.0;
        public int trail_length = 5;
        public boolean client_prediction = true;
        public double min_fall_distance = 1.5;
        public double max_drift = 1.5;
        public boolean cling_everything = false;
        public boolean dual_grip_enabled = true;
        public double dual_grip_multiplier = 1.5;
        public boolean unlimited_grip = false;
        public double release_cooldown = 0.5;
        public boolean use_mod_stamina = true;
        public double stamina_hang_cost = 4.0;
        public double stamina_climb_cost = 8.0;
        public double stamina_shimmy_cost = 8.0;
        public boolean block_other_hand = true;
        public double soft_slide_ramp = 0.05;
    }

    public static class Climbing {

        public boolean enabled = true;
        public boolean requires_dual = true;
        public double speed = 0.08;
        public double reach = 2.5;
        public boolean allow_diagonal = false;
        public int durability_cost = 1;
        public boolean damage_both_tools = true;
        public boolean stop_at_soft_block = true;
    }

    public static class Traverse {

        public boolean enabled = true;
        public boolean requires_dual = true;
        public double speed = 0.06;
        public int durability_cost = 0;
    }

    public static class Effects {

        public boolean particles = true;
        public boolean sounds = true;
        public double volume = 1.0;
    }

    public static class Launch {

        public boolean enabled = true;
        public double charge_time = 0.3;
        public double upward_boost = 0.75;
        public int max_cap_height = 3;
        public boolean auto_height = true;
        public double assist_speed = 0.15;
    }

    public static class WallJump {

        public double time_window = 0.7;
        public double forward_boost = 1.4;
        public double upward_boost = 1.1;
    }

    public static class Enchantments {

        public double spring_bonus = 0.15;
        public double traverse_bonus = 0.5;
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
}
