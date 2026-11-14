package net.gouge.physics;

import net.gouge.config.ConfigData;
import net.gouge.config.GougeConfig;
import net.gouge.enchant.GougeEnchantments;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static net.gouge.physics.Pickaxes.*;

public final class GripMotion {

    private GripMotion() {
    }

    public static final double SOFT_SLIDE_MAX_SPEED = 0.5;
    public static final double SOFT_SLIDE_MIN_SPEED = 0.3;
    public static final double SOFT_SLIDE_HARDNESS_SCALE = 0.13;
    public static final double HORIZONTAL_DAMPING = 0.5;
    public static final double HARD_FRICTION_BASE = 0.90;
    public static final double HARD_FRICTION_SCALE = 0.03;
    public static final double HARD_MIN_FRICTION = 0.55;
    public static final double HARD_MAX_FRICTION = 0.90;
    public static final double HANG_LOCK_SPEED = 0.08;
    public static final int LAUNCH_ASSIST_TICKS = 26;

    public static double softSlideVy(double vy, double slideSpeed) {
        double ramp = GougeConfig.INSTANCE.mechanics.soft_slide_ramp;
        return ramp <= 0 ? -slideSpeed : Math.max(vy - ramp, -slideSpeed);
    }

    public static double riseOf(double boost) {
        double y = 0;
        double vy = boost;
        for (int i = 0; i < 400 && vy > 0; i++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
        }
        return y;
    }

    public static double boostFor(double rise) {
        double boost = 0.1;
        while (boost < 2.0 && riseOf(boost) < rise) {
            boost += 0.01;
        }
        return boost;
    }

    public static Vec3 launchVelocity(ItemStack stack, double neededRise) {
        ConfigData.Launch l = GougeConfig.INSTANCE.launch;
        double base = l.auto_height ? boostFor(neededRise) + 0.02 : l.upward_boost * springScale(stack);
        return new Vec3(0, base, 0);
    }

    public static Vec3 assistPush(Level world, Player player, Direction face) {
        Vec3 toward = new Vec3(-face.getStepX(), 0, -face.getStepZ());
        if (GougeConfig.INSTANCE.launch.assist_speed <= 0
                || !world.noCollision(player, player.getBoundingBox().move(toward.scale(0.3)))
                || (player.getDeltaMovement().y <= 0 && !world.noCollision(player, player.getBoundingBox().move(0, -0.6, 0)))) {
            return null;
        }
        return toward.scale(GougeConfig.INSTANCE.launch.assist_speed);
    }

    public static Vec3 traverseDelta(Player player, Direction face, float leftIntent, ItemStack stack) {
        double speed = GougeConfig.INSTANCE.traverse.speed
                * (1.0 + enchLevel(stack, GougeEnchantments.SHIMMY) * GougeConfig.INSTANCE.enchantments.traverse_bonus);
        return new Vec3(-face.getStepZ() * leftIntent * speed, 0, face.getStepX() * leftIntent * speed);
    }

    public static double hangHoldSpeed() {
        ConfigData.Climbing c = GougeConfig.INSTANCE.climbing;
        return Math.max(HANG_LOCK_SPEED * 2, c.enabled ? c.speed * 1.5 : 0);
    }
}
