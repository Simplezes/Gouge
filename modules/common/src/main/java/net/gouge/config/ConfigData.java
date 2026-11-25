package net.gouge.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigData {
    public Grip grip = new Grip();
    public Slide slide = new Slide();
    public Stamina stamina = new Stamina();
    public Climbing climbing = new Climbing();
    public Shimmy shimmy = new Shimmy();
    public Launch launch = new Launch();
    public WallJump wall_jump = new WallJump();
    public Enchantments enchantments = new Enchantments();
    public Effects effects = new Effects();
    public Hud hud = new Hud();
    public Map<String, Double> block_grip = new LinkedHashMap<>();
    public Map<String, PickaxeStats> pickaxes = new LinkedHashMap<>();
    public Map<String, String> block_overrides = new LinkedHashMap<>();
    public List<String> pickaxe_blacklist = new ArrayList<>();

    public ConfigData() {
        pickaxes.put("minecraft:wooden_pickaxe", new PickaxeStats(2.0, 1));
        pickaxes.put("minecraft:stone_pickaxe", new PickaxeStats(4.0, 2));
        pickaxes.put("minecraft:golden_pickaxe", new PickaxeStats(1.5, 1));
        pickaxes.put("minecraft:iron_pickaxe", new PickaxeStats(6.0, 3));
        pickaxes.put("minecraft:diamond_pickaxe", new PickaxeStats(10.0, 5));
        pickaxes.put("minecraft:netherite_pickaxe", new PickaxeStats(15.0, 7));
        pickaxes.put("fallback", new PickaxeStats(3.0, 2));
    }

    public static class Grip {

        public double reach = 2.5;
        public double min_fall_distance = 1.5;
        public double max_drift = 1.5;
        public double slip_cooldown = 5.0;
        public double release_cooldown = 0.5;
        public boolean dual_enabled = true;
        public double dual_multiplier = 1.5;
        public boolean unlimited = false;
        public boolean cling_everything = false;
        public boolean block_other_hand = true;
        public boolean client_prediction = true;
    }

    public static class Slide {

        public double fall_damage = 0.5;
        public double fall_damage_cap = 6.0;
        public int trail_length = 5;
        public double ramp = 0.05;
    }

    public static class Stamina {

        public boolean enabled = false;
        public double refill_time = 3.0;
        public double refill_delay = 0.0;
        public double hang_cost = 12.0;
        public double climb_multiplier = 1.0;
        public double shimmy_multiplier = 1.0;
        public boolean use_mod = true;
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

    public static class Shimmy {

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

    public static class Hud {

        public boolean enabled = true;
        public double show_below = 1.0;
        public double hold_time = 0.75;
        public boolean show_pips = true;
        public boolean show_rings = true;
        public boolean blink_when_low = true;
        public boolean left_side = false;
        public int offset_x = 0;
        public int offset_y = 0;
        public int scale = 1;
        public double opacity = 1.0;
        public double mid_threshold = 0.5;
        public double low_threshold = 0.25;
        public String high_color = "#55C43A";
        public String mid_color = "#F0C933";
        public String low_color = "#D93A2C";
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
        public int spring_cap_bonus = 1;
        public double shimmy_bonus = 0.5;
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
