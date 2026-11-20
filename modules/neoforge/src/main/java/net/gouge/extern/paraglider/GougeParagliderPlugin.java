package net.gouge.extern.paraglider;

import com.mojang.logging.LogUtils;
import net.gouge.config.GougeConfig;
import net.gouge.physics.GougePhysics;
import net.gouge.physics.GripState;
import net.minecraft.resources.Identifier;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.movement.ParagliderPlayerStates;
import tictim.paraglider.api.plugin.ParagliderPlugin;
import tictim.paraglider.api.stamina.Stamina;

@ParagliderPlugin
public final class GougeParagliderPlugin implements MovementPlugin {
    private Identifier grip;

    private static double delta(double percentPerSecond) {
        return -GougePhysics.staminaCurve(percentPerSecond) * Stamina.STAMINA_PER_WHEEL / 20.0;
    }

    @Override
    public void registerNewStates(PlayerStateRegister register) {
        double cost = GougeConfig.INSTANCE.mechanics.stamina_hang_cost;
        grip = Identifier.fromNamespaceAndPath("gouge", "grip_" + Math.round(cost * 100));
        register.register(grip, delta(cost));
    }

    @Override
    public void modifyRegisteredStates(PlayerStateModifier modifier) {
        LogUtils.getLogger().info("[Gouge] Paraglider states {}", modifier.exists(grip) ? "OK" : "MISSING");
    }

    @Override
    public void registerStateConnections(PlayerStateConnectionRegister register) {
        register.connect(ParagliderPlayerStates.IDLE, grip,
                ctx -> GougePhysics.modStamina() && GripState.isActive(ctx.player().getUUID()),
                9.0);
    }
}
