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
    private static final int MAX = 100;
    private final ResourceLocation[] grip = new ResourceLocation[MAX + 1];

    private static int delta(double percentPerSecond) {
        return -(int) Math.round(GougePhysics.staminaCurve(percentPerSecond) * STAMINA_PER_WHEEL / 20.0);
    }

    private static int cost() {
        return (int) Math.round(Math.max(0, Math.min(MAX, GougeConfig.INSTANCE.stamina.hang_cost)));
    }

    @Override
    public void registerNewStates(PlayerStateRegister register) {
        for (int i = 0; i <= MAX; i++) {
            grip[i] = new ResourceLocation("gouge", "grip_" + i);
            register.register(grip[i], delta(i));
        }
    }

    @Override
    public void modifyRegisteredStates(PlayerStateModifier modifier) {
        LogUtils.getLogger().info("[Gouge] Paraglider states {}", modifier.exists(grip[MAX]) ? "OK" : "MISSING");
    }

    @Override
    public void registerStateConnections(PlayerStateConnectionRegister register) {
        for (int i = 0; i <= MAX; i++) {
            int step = i;
            register.addBranch(ParagliderPlayerStates.IDLE,
                    (player, prev, paragliding, wind) -> player instanceof ServerPlayer sp && GripState.isActive(sp.getUUID())
                            && GougePhysics.modStamina() && cost() == step,
                    grip[i], 9.0);
        }
    }
}
