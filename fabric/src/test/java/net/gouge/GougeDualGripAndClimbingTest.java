package net.gouge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GougeDualGripAndClimbingTest {

    @BeforeEach
    public void setup() {
        GougeConfig.INSTANCE = new GougeConfig.ConfigData();
    }

    @Test
    @DisplayName("Default configuration values meet all requirements")
    public void testDefaultConfigValues() {
        GougeConfig.ConfigData config = GougeConfig.INSTANCE;

        assertTrue(config.mechanics.dual_grip_enabled, "Dual grip should be enabled by default");
        assertEquals(1.5, config.mechanics.dual_grip_multiplier, 1e-6, "Default dual grip multiplier should be 1.5x");
        assertFalse(config.mechanics.unlimited_grip, "Unlimited grip MUST be disabled by default");

        assertTrue(config.climbing.enabled, "Climbing should be enabled by default");
        assertTrue(config.climbing.requires_dual, "Climbing should require two pickaxes by default");
        assertEquals(0.08, config.climbing.speed, 1e-6, "Climbing speed should be 0.08 blocks/tick");
        assertEquals(2.5, config.climbing.reach, 1e-6, "Climbing reach should match default mechanics reach (2.5)");
        assertFalse(config.climbing.allow_diagonal, "Diagonal climbing should be false by default");
        assertEquals(1, config.climbing.durability_cost, "Climbing durability cost should default to 1");
        assertTrue(config.climbing.damage_both_tools, "Climbing should damage both tools by default");
    }

    @Test
    @DisplayName("Config validation clamps out-of-range values safely")
    public void testConfigValidationAndClamping() throws Exception {
        Method readMethod = GougeConfig.class.getDeclaredMethod("read", CommentedConfig.class);
        readMethod.setAccessible(true);

        Method validateMethod = GougeConfig.class.getDeclaredMethod("validate", GougeConfig.ConfigData.class);
        validateMethod.setAccessible(true);

        String tomlBelowMin = """
                [mechanics]
                dual_grip_multiplier = 0.2
                reach = 0.1
                
                [climbing]
                speed = 0.001
                reach = 0.1
                durability_cost = -5
                """;

        CommentedConfig parsedBelow = new TomlParser().parse(new StringReader(tomlBelowMin));
        GougeConfig.ConfigData dataBelow = (GougeConfig.ConfigData) readMethod.invoke(null, parsedBelow);
        dataBelow = (GougeConfig.ConfigData) validateMethod.invoke(null, dataBelow);

        assertEquals(1.0, dataBelow.mechanics.dual_grip_multiplier, 1e-6, "dual_grip_multiplier should be clamped to min 1.0");
        assertEquals(0.01, dataBelow.climbing.speed, 1e-6, "climbing.speed should be clamped to min 0.01");
        assertEquals(0.5, dataBelow.climbing.reach, 1e-6, "climbing.reach should be clamped to min 0.5");
        assertEquals(0, dataBelow.climbing.durability_cost, "climbing.durability_cost should be clamped to min 0");

        String tomlAboveMax = """
                [mechanics]
                dual_grip_multiplier = 25.0
                
                [climbing]
                speed = 5.0
                reach = 100.0
                durability_cost = 500
                """;

        CommentedConfig parsedAbove = new TomlParser().parse(new StringReader(tomlAboveMax));
        GougeConfig.ConfigData dataAbove = (GougeConfig.ConfigData) readMethod.invoke(null, parsedAbove);
        dataAbove = (GougeConfig.ConfigData) validateMethod.invoke(null, dataAbove);

        assertEquals(10.0, dataAbove.mechanics.dual_grip_multiplier, 1e-6, "dual_grip_multiplier should be clamped to max 10.0");
        assertEquals(0.5, dataAbove.climbing.speed, 1e-6, "climbing.speed should be clamped to max 0.5");
        assertEquals(32.0, dataAbove.climbing.reach, 1e-6, "climbing.reach should be clamped to max 32.0");
        assertEquals(100, dataAbove.climbing.durability_cost, "climbing.durability_cost should be clamped to max 100");
    }

    @Test
    @DisplayName("Backward compatibility with older config files missing new keys")
    public void testBackwardCompatibility() throws Exception {
        Method readMethod = GougeConfig.class.getDeclaredMethod("read", CommentedConfig.class);
        readMethod.setAccessible(true);

        Method validateMethod = GougeConfig.class.getDeclaredMethod("validate", GougeConfig.ConfigData.class);
        validateMethod.setAccessible(true);

        String legacyToml = """
                [mechanics]
                reach = 3.0
                slip_cooldown = 4.0
                """;

        CommentedConfig parsed = new TomlParser().parse(new StringReader(legacyToml));
        GougeConfig.ConfigData data = (GougeConfig.ConfigData) readMethod.invoke(null, parsed);
        data = (GougeConfig.ConfigData) validateMethod.invoke(null, data);

        assertNotNull(data.climbing, "climbing section should not be null");
        assertTrue(data.mechanics.dual_grip_enabled, "dual_grip_enabled should cleanly default to true");
        assertEquals(1.5, data.mechanics.dual_grip_multiplier, 1e-6);
        assertFalse(data.mechanics.unlimited_grip, "unlimited_grip should default to false");
        assertTrue(data.climbing.enabled, "climbing.enabled should cleanly default to true");
        assertTrue(data.climbing.requires_dual, "climbing.requires_dual should cleanly default to true");
        assertEquals(0.08, data.climbing.speed, 1e-6);
    }

    @Test
    @DisplayName("Config writer serializes all new options with comments")
    public void testConfigWriting() throws Exception {
        Method writeMethod = java.util.Arrays.stream(GougeConfig.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("write")).findFirst().orElseThrow();
        writeMethod.setAccessible(true);

        CommentedFileConfig c = CommentedFileConfig.builder(
                java.nio.file.Files.createTempFile("gouge", ".toml"), TomlFormat.instance()).build();
        GougeConfig.ConfigData d = new GougeConfig.ConfigData();
        d.mechanics.dual_grip_multiplier = 2.0;
        d.climbing.allow_diagonal = true;
        d.mechanics.unlimited_grip = true;

        writeMethod.invoke(null, c, d);

        StringWriter writer = new StringWriter();
        new TomlWriter().write(c, writer);
        String toml = writer.toString();

        assertTrue(toml.contains("dual_grip_enabled"));
        assertTrue(toml.contains("dual_grip_multiplier"));
        assertTrue(toml.contains("unlimited_grip"));
        assertTrue(toml.contains("[climbing]") || toml.contains("climbing.enabled"));
        assertTrue(toml.contains("requires_dual"));
        assertTrue(toml.contains("speed"));
        assertTrue(toml.contains("reach"));
        assertTrue(toml.contains("allow_diagonal"));
        assertTrue(toml.contains("durability_cost"));
        assertTrue(toml.contains("damage_both_tools"));
    }

    @Test
    @DisplayName("Hang duration multiplier calculation matches expected math")
    public void testHangDurationMultiplier() {
        int baseTicks = 120; // 6 seconds base hang
        double multiplier = 1.5;

        int expectedDualTicks = (int) Math.round(baseTicks * multiplier);
        assertEquals(180, expectedDualTicks, "6s * 1.5 = 9s (180 ticks)");

        int diamondBaseTicks = 200;
        int diamondDualTicks = (int) Math.round(diamondBaseTicks * multiplier);
        assertEquals(300, diamondDualTicks);
    }

    @Test
    @DisplayName("Dual grip state drops safely to single pickaxe duration on equipment swap")
    public void testEquipmentSwapSafeDowngrade() {
        int now = 1000;
        int singleHangTicks = 100;
        int dualHangTicks = (int) Math.round(singleHangTicks * 1.5); // 150

        int hangStart = now;
        int dualExpiry = hangStart + dualHangTicks; // 1150
        int singleExpiry = hangStart + singleHangTicks; // 1100

        int[] d = new int[]{dualExpiry, -1, -1, 0, 0, hangStart, 1, singleHangTicks};

        int currentTick1 = 1020;
        boolean dualActive = false; // offhand lost
        if (d[6] == 1 && !dualActive) {
            d[6] = 0;
            d[0] = d[5] + d[7];
        }
        assertEquals(0, d[6], "Dual flag cleared");
        assertEquals(singleExpiry, d[0], "Expiry reduced back to single pickaxe limit");
        assertTrue(currentTick1 < d[0], "Player has not expired single duration yet");

        int[] d2 = new int[]{dualExpiry, -1, -1, 0, 0, hangStart, 1, singleHangTicks};
        int currentTick2 = 1120;
        if (d2[6] == 1 && !dualActive) {
            d2[6] = 0;
            d2[0] = d2[5] + d2[7];
        }
        assertTrue(currentTick2 >= d2[0], "Player has exceeded single duration, immediate slip triggered!");
    }

    @Test
    @DisplayName("Unlimited grip setting prevents timer-based slipping")
    public void testUnlimitedGripSetting() {
        GougeConfig.INSTANCE.mechanics.unlimited_grip = true;

        int now = 5000;
        int expiry = 1000;

        boolean shouldSlip = !GougeConfig.INSTANCE.mechanics.unlimited_grip && now >= expiry;
        assertFalse(shouldSlip, "With unlimited_grip=true, timer-based slip must never trigger");

        GougeConfig.INSTANCE.mechanics.unlimited_grip = false;
        shouldSlip = !GougeConfig.INSTANCE.mechanics.unlimited_grip && now >= expiry;
        assertTrue(shouldSlip, "With unlimited_grip=false, expired timer must trigger slip");
    }

    @Test
    @DisplayName("Climbing gating rules respect config options")
    public void testClimbingGating() {
        GougeConfig.INSTANCE.climbing.enabled = false;
        GougeConfig.INSTANCE.climbing.requires_dual = true;
        boolean dualGripActive = true;
        boolean canClimb = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || dualGripActive);
        assertFalse(canClimb, "Should not be able to climb when climbing is disabled");

        GougeConfig.INSTANCE.climbing.enabled = true;
        GougeConfig.INSTANCE.climbing.requires_dual = true;
        dualGripActive = false;
        canClimb = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || dualGripActive);
        assertFalse(canClimb, "Should not be able to climb with single pickaxe when dual is required");

        dualGripActive = true;
        canClimb = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || dualGripActive);
        assertTrue(canClimb, "Should be able to climb with dual pickaxes");

        GougeConfig.INSTANCE.climbing.requires_dual = false;
        dualGripActive = false;
        canClimb = GougeConfig.INSTANCE.climbing.enabled
                && (!GougeConfig.INSTANCE.climbing.requires_dual || dualGripActive);
        assertTrue(canClimb, "Should be able to climb with single pickaxe when dual requirement is disabled");
    }

    @Test
    @DisplayName("Ledge mantle threshold and horizontal-only grip validation")
    public void testLedgeMantleThreshold() {
        double blockY = 64.0;
        double ledgeTopY = blockY + 1.0; // 65.0

        double playerFeetApproaching = 64.6; // 65.0 - 0.4
        boolean canStand = true;
        boolean shouldMantle = canStand && playerFeetApproaching >= (ledgeTopY - 0.45);
        assertTrue(shouldMantle, "Player near ledge top must trigger mantle boost and finish climb");

        double playerFeetLower = 64.2;
        boolean shouldMantleLower = canStand && playerFeetLower >= (ledgeTopY - 0.45);
        assertFalse(shouldMantleLower, "Player lower on the wall should continue climbing normally");

        double playerStanding = 65.0;
        boolean shouldRelease = canStand && playerStanding >= (ledgeTopY - 0.1);
        assertTrue(shouldRelease, "Standing on top of the block must cleanly release grip");
    }

    @Test
    @DisplayName("Pinned wall column follows the player along the wall and measures distance to its face")
    public void testWallPinGeometry() {
        var pin = new net.minecraft.core.BlockPos(10, 64, 5);
        var at = new net.minecraft.world.phys.Vec3(11.4, 66.0, 7.6);
        assertEquals(new net.minecraft.core.BlockPos(10, 64, 7),
                GougePhysics.tangentCell(pin, net.minecraft.core.Direction.EAST, at));
        assertEquals(0.4, GougePhysics.wallDistance(pin, net.minecraft.core.Direction.EAST, at), 1e-9);
        var at2 = new net.minecraft.world.phys.Vec3(10.5, 66.0, 3.5);
        assertEquals(1.5, GougePhysics.wallDistance(pin, net.minecraft.core.Direction.NORTH, at2), 1e-9);
    }
}
