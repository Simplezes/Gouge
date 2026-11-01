package net.gouge.extern.parcool;

import net.gouge.GougePhysics;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class ParCool4Compat {
    private static final Set<String> WALL_ACTIONS =
            Set.of("HangOn", "SlideDown", "WallJump", "WallRun", "HorizontalWallRun", "Crawl");
    private static final String EVENT = "com.alrex.parcool.api.action.ParCoolActionEvent";

    private ParCool4Compat() {}

    @SuppressWarnings("unchecked")
    static void register(Set<UUID> active) throws ReflectiveOperationException {
        Method getPlayer = Class.forName(EVENT).getMethod("getPlayer");
        Method getAction = Class.forName(EVENT).getMethod("getAction");

        listen("$TryToStart", getPlayer, getAction, (id, wall, e) -> {
            if (wall && GougePhysics.isActive(id)) e.setCanceled(true);
        });
        listen("$Start$Pre", getPlayer, getAction, (id, wall, e) -> {
            if (wall) active.add(id);
        });
        listen("$Finish$Post", getPlayer, getAction, (id, wall, e) -> {
            if (wall) active.remove(id);
        });
    }

    private interface Handler {
        void accept(UUID id, boolean wall, Event e);
    }

    @SuppressWarnings("unchecked")
    private static void listen(String suffix, Method getPlayer, Method getAction, Handler h)
            throws ReflectiveOperationException {
        Class<Event> type = (Class<Event>) Class.forName(EVENT + suffix);
        Consumer<Event> c = e -> {
            try {
                UUID id = ((net.minecraft.world.entity.player.Player) getPlayer.invoke(e)).getUUID();
                h.accept(id, WALL_ACTIONS.contains(getAction.invoke(e).getClass().getSimpleName()), e);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, c);
    }
}
