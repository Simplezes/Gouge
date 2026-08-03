package net.gouge.extern.parcool;

import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.impl.ClingToCliff;
import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.action.impl.HorizontalWallRun;
import com.alrex.parcool.common.action.impl.VerticalWallRun;
import com.alrex.parcool.common.action.impl.WallJump;
import com.alrex.parcool.common.action.impl.WallSlide;
import net.gouge.GougePhysics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParCoolCompat {
    private static final Set<UUID> wallActionActive = ConcurrentHashMap.newKeySet();

    private ParCoolCompat() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(new Listener());
    }

    public static boolean isWallActionActive(UUID id) {
        return wallActionActive.contains(id);
    }

    private static boolean isWallAction(Action action) {
        return action instanceof ClingToCliff || action instanceof WallSlide
                || action instanceof WallJump || action instanceof VerticalWallRun
                || action instanceof HorizontalWallRun
                || action instanceof Crawl;
    }

    private static final class Listener {
        @SubscribeEvent
        public void onTryToStart(ParCoolActionEvent.TryToStart event) {
            if (isWallAction(event.getAction()) && GougePhysics.isActive(event.getPlayer().getUUID())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onStartPre(ParCoolActionEvent.Start.Pre event) {
            if (isWallAction(event.getAction())) {
                wallActionActive.add(event.getPlayer().getUUID());
            }
        }

        @SubscribeEvent
        public void onFinishPost(ParCoolActionEvent.Finish.Post event) {
            if (isWallAction(event.getAction())) {
                wallActionActive.remove(event.getPlayer().getUUID());
            }
        }
    }
}
