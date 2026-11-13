package net.gouge.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.util.List;

import static net.gouge.config.GougeConfig.*;

public final class ConfigWriter {

    static void write(CommentedConfig c, ConfigData d) {
        c.setComment("mechanics", """
                 =======================================================
                 GOUGE CONFIGURATION

                 Tip: Save this file and run '/gouge reload' in-game!
                 Values outside the listed range are clamped on load.
                 =======================================================""");
        c.set("mechanics.reach", d.mechanics.reach);
        c.setComment("mechanics.reach", """
                 How far from a wall you can be and still grab onto it. Default is 2.5 blocks.
                 Increase this if grabbing feels too hard to trigger. Range: 0.5 - 32.0""");
        c.set("mechanics.slip_cooldown", d.mechanics.slip_cooldown);
        c.setComment("mechanics.slip_cooldown", """
                 After slipping off a wall, how long before you can grab again (in seconds).
                 Set to 0 to remove the cooldown entirely. Range: 0 - 600""");
        c.set("mechanics.soft_fall_damage", d.mechanics.soft_fall_damage);
        c.setComment("mechanics.soft_fall_damage", """
                 When you land on a soft block (dirt, grass, leaves, etc.) after sliding,
                 this multiplies the fall damage you take. 0.5 (50%) means half damage, 0.0 means none.
                 Range: 0.0 - 1.0""");
        c.set("mechanics.soft_fall_damage_cap", d.mechanics.soft_fall_damage_cap);
        c.setComment("mechanics.soft_fall_damage_cap", """
                 Maximum damage a soft-block slide can ever deal, in half-hearts (2 = 1 heart),
                 no matter how long the slide was. Set to 0 to disable the cap. Range: 0 - 40""");
        c.set("mechanics.trail_length", d.mechanics.trail_length);
        c.setComment("mechanics.trail_length", """
                 How many blocks get the cracking visual effect as you slide down a wall.
                 Higher = longer crack trail behind you. Set to 0 to disable. Range: 0 - 16""");
        c.set("mechanics.client_prediction", d.mechanics.client_prediction);
        c.setComment("mechanics.client_prediction", """
                 Lets your own game predict the wall slide instead of waiting for the server to
                 correct you every tick. This is what keeps the slide smooth instead of jittery.
                 This is a client-side setting: it only affects your own screen, never other players.
                 Turn it off if the slide ever fights you or looks wrong on a modded server.""");
        c.set("mechanics.min_fall_distance", d.mechanics.min_fall_distance);
        c.setComment("mechanics.min_fall_distance", """
                 How much open air needs to be below you to grab a wall (in blocks), checked with
                 an instant raycast rather than waiting for you to actually fall that far. Stops a
                 simple jump/step from letting you attach. Range: 0.0 - 32.0""");
        c.set("mechanics.max_drift", d.mechanics.max_drift);
        c.setComment("mechanics.max_drift", """
                 How far you can drift sideways away from the spot you grabbed before you slip off
                 (in blocks). Stops you from walking/gliding along a wall indefinitely.
                 Range: 0.0 - 16.0""");
        c.set("mechanics.cling_everything", d.mechanics.cling_everything);
        c.setComment("mechanics.cling_everything", """
                 Treat every block as a hard (hang-able) block, regardless of the vanilla
                 hard/soft default or the block_overrides list below. Turn this on if you
                 just want to cling to anything - leaves, wood, dirt, everything - without
                 listing each block individually. block_overrides entries still win over this.""");
        c.set("mechanics.dual_grip_enabled", d.mechanics.dual_grip_enabled);
        c.setComment("mechanics.dual_grip_enabled", """
                 Enables or disables dual-pickaxe grip. When holding a qualifying pickaxe
                 in both hands, your grip duration is extended by the multiplier below.""");
        c.set("mechanics.dual_grip_multiplier", d.mechanics.dual_grip_multiplier);
        c.setComment("mechanics.dual_grip_multiplier", """
                 Multiplier applied to hang time when gripping with pickaxes in both hands.
                 1.5 means 50% longer hang time. Range: 1.0 - 10.0""");
        c.set("mechanics.unlimited_grip", d.mechanics.unlimited_grip);
        c.setComment("mechanics.unlimited_grip", """
                 Sandbox setting: allows infinite hang duration on walls without slipping.
                 Disabled by default (false). Durability drain still applies unless damage_rate is 0.""");
        c.set("mechanics.release_cooldown", d.mechanics.release_cooldown);
        c.setComment("mechanics.release_cooldown", """
                 After letting go of a wall on purpose (releasing the use key or switching away
                 from the pickaxe), how long before you can grab again (in seconds). This makes
                 releasing cost some height. Never shortens a longer slip/wall-jump cooldown.
                 Set to 0 to disable. Range: 0 - 600""");
        c.set("mechanics.use_mod_stamina", d.mechanics.use_mod_stamina);
        c.setComment("mechanics.use_mod_stamina", """
                 When a stamina mod (ParCool) is installed, gripping, climbing and shimmying
                 spend its stamina instead of using the hang_time grip timer. You slip when it
                 runs out. Has no effect without such a mod. Set to false to always use hang_time.""");
        c.set("mechanics.stamina_hang_cost", d.mechanics.stamina_hang_cost);
        c.setComment("mechanics.stamina_hang_cost", """
                 Stamina spent per second while hanging, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.stamina_climb_cost", d.mechanics.stamina_climb_cost);
        c.setComment("mechanics.stamina_climb_cost", """
                 Stamina spent per second while climbing, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.stamina_shimmy_cost", d.mechanics.stamina_shimmy_cost);
        c.setComment("mechanics.stamina_shimmy_cost", """
                 Stamina spent per second while shimmying sideways, as a percent of the stamina bar.
                 Only used with use_mod_stamina. Range: 0 - 100""");
        c.set("mechanics.block_other_hand", d.mechanics.block_other_hand);
        c.setComment("mechanics.block_other_hand", """
                 While a pickaxe in one hand can grip a wall (or is gripping), items in the other
                 hand (bow, food, shield, etc.) cannot be used. Set to false to allow using both.""");
        c.set("mechanics.soft_slide_ramp", d.mechanics.soft_slide_ramp);
        c.setComment("mechanics.soft_slide_ramp", """
                 How quickly a soft-block slide speeds up, in blocks per tick of extra speed each
                 tick. Lower = gentler start. Set to 0 to jump straight to full slide speed.
                 Range: 0.0 - 1.0""");

        c.setComment("climbing", """
                 =======================================================
                 CLIMBING SETTINGS
                 Controls optional pickaxe climbing mechanics.
                 =======================================================""");
        c.set("climbing.enabled", d.climbing.enabled);
        c.setComment("climbing.enabled", """
                 Enables or disables climbing independently. When enabled, holding Space
                 while gripping a wall lets you climb up the surface.""");
        c.set("climbing.requires_dual", d.climbing.requires_dual);
        c.setComment("climbing.requires_dual", """
                 If true, climbing requires holding a pickaxe in both hands. If false,
                 a single pickaxe is sufficient to climb.""");
        c.set("climbing.speed", d.climbing.speed);
        c.setComment("climbing.speed", """
                 Upward movement speed while climbing (in blocks per tick).
                 Default is 0.08. Range: 0.01 - 0.5""");
        c.set("climbing.reach", d.climbing.reach);
        c.setComment("climbing.reach", """
                 Maximum wall reach distance allowed while climbing (in blocks).
                 Default is 2.5. Range: 0.5 - 32.0""");
        c.set("climbing.allow_diagonal", d.climbing.allow_diagonal);
        c.setComment("climbing.allow_diagonal", """
                 Allows moving sideways along the wall while climbing (diagonal climbing).
                 Default is false (vertical only).""");
        c.set("climbing.durability_cost", d.climbing.durability_cost);
        c.setComment("climbing.durability_cost", """
                 Extra durability drained per second while actively climbing.
                 Default is 1. Range: 0 - 100""");
        c.set("climbing.damage_both_tools", d.climbing.damage_both_tools);
        c.setComment("climbing.damage_both_tools", """
                 If true and two pickaxes are held, climbing durability damage is dealt
                 to both pickaxes. If false, only the active tool takes damage.""");
        c.set("climbing.stop_at_soft_block", d.climbing.stop_at_soft_block);
        c.setComment("climbing.stop_at_soft_block", """
                 If true, climbing stops when your hands reach a soft block (dirt, leaves, etc.)
                 so you cannot climb up through soft blocks. If false, soft blocks above do not stop you.""");

        c.setComment("effects", """
                 =======================================================
                 EFFECTS
                 Particles and sounds for shimmying, climbing, launching and gripping.
                 =======================================================""");
        c.set("effects.particles", d.effects.particles);
        c.setComment("effects.particles", " Show chip and dust particles. Gameplay is not affected.");
        c.set("effects.sounds", d.effects.sounds);
        c.setComment("effects.sounds", " Play grip, scrape and launch sounds. Gameplay is not affected.");
        c.set("effects.volume", d.effects.volume);
        c.setComment("effects.volume", " Volume multiplier for Gouge sounds. Range: 0.0 - 2.0");

        c.setComment("traverse", """
                 =======================================================
                 TRAVERSE SETTINGS
                 Shimmy sideways along a wall while hanging (hold A / D).
                 Uses the normal grip timer and durability drain.
                 =======================================================""");
        c.set("traverse.enabled", d.traverse.enabled);
        c.setComment("traverse.enabled", " Lets you move left and right along a wall while hanging on it.");
        c.set("traverse.requires_dual", d.traverse.requires_dual);
        c.setComment("traverse.requires_dual", " If true, traversing requires a pickaxe in both hands.");
        c.set("traverse.speed", d.traverse.speed);
        c.setComment("traverse.speed", " Sideways speed in blocks per tick. Range: 0.01 - 0.3");
        c.set("traverse.durability_cost", d.traverse.durability_cost);
        c.setComment("traverse.durability_cost", """
                 Extra durability drained per second while shimmying sideways, on top of the
                 normal hang drain. Default is 0. Range: 0 - 100""");

        c.setComment("launch", """
                 =======================================================
                 LAUNCH SETTINGS
                 When a climb is stopped by a short cap of soft blocks (dirt, grass...) on top
                 of the rock, keep holding Space and you spring up and over it.
                 =======================================================""");
        c.set("launch.enabled", d.launch.enabled);
        c.setComment("launch.enabled", " Enables the climb-over-soft-cap launch.");
        c.set("launch.charge_time", d.launch.charge_time);
        c.setComment("launch.charge_time", """
                 How long (seconds) you must keep holding Space against the soft cap before you
                 launch. Prevents accidental launches. Range: 0.0 - 5.0""");
        c.set("launch.upward_boost", d.launch.upward_boost);
        c.setComment("launch.upward_boost", """
                 Upward launch power used when auto_height is off. 0.75 clears about 2 blocks.
                 With auto_height on this value is ignored. Range: 0.1 - 2.0""");
        c.set("launch.auto_height", d.launch.auto_height);
        c.setComment("launch.auto_height", """
                 Sizes the launch to land you on top of the cap, so 1, 2, 3 or 4 block caps all
                 work without overshooting. Turn off to always use exactly upward_boost.""");
        c.set("launch.assist_speed", d.launch.assist_speed);
        c.setComment("launch.assist_speed", """
                 Gentle push towards the wall while you rise, so you land on top of the cap
                 instead of falling back. Set to 0 to disable. Range: 0.0 - 0.5""");
        c.set("launch.max_cap_height", d.launch.max_cap_height);
        c.setComment("launch.max_cap_height", """
                 Tallest soft cap (in blocks) you can launch over. The cap must have standable
                 ground on top, otherwise there is nothing to land on. Range: 1 - 4""");

        c.set("wall_jump.time_window", d.wall_jump.time_window);
        c.setComment("wall_jump.time_window", """
                 How quickly you need to double-tap crouch to trigger the wall jump (in seconds).
                 Tap crouch once to release, then tap it again within this window to jump.
                 Works on both hard and soft blocks. Range: 0.05 - 5.0""");
        c.set("wall_jump.forward_boost", d.wall_jump.forward_boost);
        c.setComment("wall_jump.forward_boost", """
                 How hard you get launched away from the wall horizontally when wall jumping.
                 1.4 is default. Lower this if ragdoll mods or other mods react badly to the speed change.
                 Range: 0.0 - 5.0""");
        c.set("wall_jump.upward_boost", d.wall_jump.upward_boost);
        c.setComment("wall_jump.upward_boost", """
                 How hard you get launched upward when wall jumping.
                 1.1 is default. This is what gives you height on the jump. Range: 0.0 - 5.0""");

        c.set("enchantments.spring_bonus", d.enchantments.spring_bonus);
        c.setComment("enchantments.spring_bonus", """
                 Extra launch and wall-kick power per level of the Spring enchantment.
                 0.15 means +15% per level. Range: 0 - 2""");
        c.set("enchantments.traverse_bonus", d.enchantments.traverse_bonus);
        c.setComment("enchantments.traverse_bonus", """
                 Extra shimmy speed per level of the Shimmy enchantment.
                 0.5 means +50% per level. Range: 0 - 5""");
        c.set("enchantments.grip_bonus", d.enchantments.grip_bonus);
        c.setComment("enchantments.grip_bonus", """
                 How many extra seconds of hang time you get per level of the Grip enchantment.
                 Grip II with 2.0 here gives you 4 extra seconds before slipping. Range: 0 - 60""");
        c.set("enchantments.momentum_reduction", d.enchantments.momentum_reduction);
        c.setComment("enchantments.momentum_reduction", """
                 How many seconds are removed from your slip cooldown per level of the Momentum enchantment.
                 Momentum III with 1.0 here cuts 3 seconds off your cooldown. Range: 0 - 60""");

        c.setComment("pickaxes", """
                 =======================================================
                 PICKAXE STATS
                 Controls how long each pickaxe lets you hang and how fast it breaks.
                 Durability drains while hanging on hard blocks AND while sliding on soft ones.

                 Keys can be:
                   - A specific item:  "minecraft:diamond_pickaxe"
                   - An item tag:      "#c:pickaxes"  (covers all pickaxes from that tag)
                   - "fallback"        used if nothing else matches

                 Priority order: Specific Item > Tag > fallback
                 Invalid keys are logged and skipped instead of crashing.
                 =======================================================""");
        boolean first = true;
        for (var entry : d.pickaxes.entrySet()) {
            List<String> hangPath = List.of("pickaxes", entry.getKey(), "hang_time");
            List<String> dmgPath = List.of("pickaxes", entry.getKey(), "damage_rate");
            c.set(hangPath, entry.getValue().hang_time);
            c.set(dmgPath, entry.getValue().damage_rate);
            if (first) {
                c.setComment(hangPath, " How long you can hang before slipping (seconds). Range: 0 - 3600");
                c.setComment(dmgPath, " How many durability points are drained per second. Range: 0 - 1000");
                first = false;
            }
        }

        c.set("block_overrides", CommentedConfig.inMemory());
        c.setComment("block_overrides", """
                 =======================================================
                 BLOCK OVERRIDES
                 Decides whether a block hangs like solid rock ("hard") or lets you
                 slide down it ("soft"). You do not need to list every block here.

                 By default:
                   - Vanilla Minecraft blocks: hard if they require the correct tool
                     for drops (stone, ores, bricks, ...), soft otherwise (sand, dirt,
                     wood, leaves, ...).
                   - Any block from another mod: hard, unless listed below.

                 Add an entry to override that default for a specific block, e.g.:
                   "create:andesite_casing" = "soft"
                   "minecraft:sandstone" = "hard"
                 Values must be "hard" or "soft".
                 =======================================================""");
        for (var entry : d.block_overrides.entrySet()) {
            c.set(List.of("block_overrides", entry.getKey()), entry.getValue());
        }

        c.set("block_grip", CommentedConfig.inMemory());
        c.setComment("block_grip", """
                 =======================================================
                 BLOCK GRIP
                 Multiplies hang time on specific blocks (or block tags). Optional.
                 1.0 = normal, 0.5 = slips twice as fast, 2.0 = holds twice as long.
                 Example:
                   "minecraft:ice" = 0.25
                   "#minecraft:logs" = 1.5
                 Range per entry: 0.0 - 10.0
                 =======================================================""");
        for (var entry : d.block_grip.entrySet()) {
            c.set(List.of("block_grip", entry.getKey()), entry.getValue());
        }
    }
}
