package net.gouge.extern.paraglider;

import com.mojang.logging.LogUtils;
import net.gouge.config.GougeConfig;
import net.gouge.physics.GougePhysics;
import net.gouge.physics.GripState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.movement.ParagliderPlayerStates;

public final class GougeParagliderPlugin implements MovementPlugin {
    private static final int STAMINA_PER_WHEEL = 1000;
    private static final ResourceLocation HANG = new ResourceLocation("gouge", "hang");
    private static final ResourceLocation CLIMB = new ResourceLocation("gouge", "climb");
    private static final ResourceLocation SHIMMY = new ResourceLocation("gouge", "shimmy");

    private static int delta(double percentPerSecond) {
        return -(int) Math.round(percentPerSecond / 100.0 * STAMINA_PER_WHEEL / 20.0);
    }

    @Override
    public void registerNewStates(PlayerStateRegister register) {
        var m = GougeConfig.INSTANCE.mechanics;
        register.register(HANG, delta(m.stamina_hang_cost));
        register.register(CLIMB, delta(m.stamina_climb_cost));
        register.register(SHIMMY, delta(m.stamina_shimmy_cost));
    }

    @Override
    public void modifyRegisteredStates(PlayerStateModifier modifier) {
        boolean ok = modifier.exists(HANG) && modifier.exists(CLIMB) && modifier.exists(SHIMMY);
        LogUtils.getLogger().info("[Gouge] Paraglider states {}", ok ? "OK" : "MISSING");
    }

    @Override
    public void registerStateConnections(PlayerStateConnectionRegister register) {
        connect(register, HANG, 0);
        connect(register, CLIMB, 1);
        connect(register, SHIMMY, 2);
    }

    private static void connect(PlayerStateConnectionRegister register, ResourceLocation state, int mode) {
        register.addBranch(ParagliderPlayerStates.IDLE,
                (player, prev, paragliding, wind) -> GougePhysics.modStamina()
                        && player instanceof ServerPlayer sp && GripState.isActive(sp.getUUID())
                        && GougePhysics.gripMode(sp) == mode,
                state, 9.0);
    }
}
