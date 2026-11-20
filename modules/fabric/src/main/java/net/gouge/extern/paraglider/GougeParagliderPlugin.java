package net.gouge.extern.paraglider;

import com.mojang.logging.LogUtils;
import net.gouge.config.GougeConfig;
import net.gouge.physics.GougePhysics;
import net.gouge.physics.GripState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.movement.ParagliderPlayerStates;
import tictim.paraglider.api.plugin.ParagliderPlugin;

@ParagliderPlugin
public final class GougeParagliderPlugin implements MovementPlugin {
    private static final int STAMINA_PER_WHEEL = 1000;
    private ResourceLocation grip;

    private static int delta(double percentPerSecond) {
        return -(int) Math.round(GougePhysics.staminaCurve(percentPerSecond) * STAMINA_PER_WHEEL / 20.0);
    }

    @Override
    public void registerNewStates(PlayerStateRegister register) {
        double cost = GougeConfig.INSTANCE.mechanics.stamina_hang_cost;
        grip = new ResourceLocation("gouge", "grip_" + Math.round(cost * 100));
        register.register(grip, delta(cost));
    }

    @Override
    public void modifyRegisteredStates(PlayerStateModifier modifier) {
        LogUtils.getLogger().info("[Gouge] Paraglider states {}", modifier.exists(grip) ? "OK" : "MISSING");
    }

    @Override
    public void registerStateConnections(PlayerStateConnectionRegister register) {
        register.addBranch(ParagliderPlayerStates.IDLE,
                (player, prev, paragliding, wind) -> GougePhysics.modStamina()
                        && player instanceof ServerPlayer sp && GripState.isActive(sp.getUUID()),
                grip, 9.0);
    }
}
