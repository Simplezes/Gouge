package net.gouge.extern.paraglider;

import com.mojang.logging.LogUtils;
import net.gouge.config.GougeConfig;
import net.gouge.physics.GougePhysics;
import net.gouge.physics.GripState;
import net.minecraft.resources.ResourceLocation;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.movement.ParagliderPlayerStates;
import tictim.paraglider.api.plugin.ParagliderPlugin;
import tictim.paraglider.api.stamina.Stamina;

@ParagliderPlugin
public final class GougeParagliderPlugin implements MovementPlugin {
    private static final ResourceLocation HANG = ResourceLocation.fromNamespaceAndPath("gouge", "hang");
    private static final ResourceLocation CLIMB = ResourceLocation.fromNamespaceAndPath("gouge", "climb");
    private static final ResourceLocation SHIMMY = ResourceLocation.fromNamespaceAndPath("gouge", "shimmy");

    private static double delta(double percentPerSecond) {
        return -percentPerSecond / 100.0 * Stamina.STAMINA_PER_WHEEL / 20.0;
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
        register.connect(ParagliderPlayerStates.IDLE, state,
                ctx -> GougePhysics.modStamina() && GripState.isActive(ctx.player().getUUID())
                        && GougePhysics.gripMode(ctx.player()) == mode,
                9.0);
    }
}
