package net.gouge.client;

import net.gouge.config.ConfigData;
import net.gouge.config.GougeConfig;
import net.gouge.physics.GougeClientPhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class GripHud {

    private static final int OUTLINE = 0x141414;
    private static final int[] STONE = {0x505050, 0x6C6C6C, 0x363636};
    private static final int[] GOLD = {0xF0C933, 0xFFF08A, 0xA8780C};
    private static final int[] PIPS_ONE = {11};
    private static final int[] PIPS_TWO = {7, 15};
    private static final int DEFAULT_HIGH = 0x55C43A;
    private static final int DEFAULT_MID = 0xF0C933;
    private static final int DEFAULT_LOW = 0xD93A2C;
    private static final float LIT_ALPHA = 0.9f;
    private static final float TRACK_ALPHA = 0.28f;
    private static final float OUTLINE_ALPHA = 0.4f;
    private static final float DIM = 0.55f;
    private static final float SPENT = 0.02f;
    private static final int PIP_Y = 28;
    private static final int GRID_W = WheelLayout.SIZE + 2;
    private static final int GRID_H = PIP_Y + 5 + 2;
    private static final int[] GRID = new int[GRID_W * GRID_H];
    private static final int[][] PALETTE = new int[3][];

    private static String paletteKey;
    private static float level;
    private static int rings = 1;
    private static int pips = 1;
    private static long lastSeen;
    private static long lastHang;

    private GripHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        ConfigData.Hud cfg = GougeConfig.INSTANCE.hud;
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.enabled || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        long now = System.currentTimeMillis();
        float live = GougeClientPhysics.staminaLevel(delta.getGameTimeDeltaPartialTick(false));
        if (live < 0f) {
            return;
        }
        boolean hanging = GougeClientPhysics.isHanging();
        long holdMs = (long) (cfg.hold_time * 1000.0);
        level = live;
        rings = cfg.show_rings ? GougeClientPhysics.gripRings() : 1;
        if (hanging) {
            pips = GougeClientPhysics.gripPips();
            lastHang = now;
        }
        boolean want = cfg.show_below >= 1.0 ? hanging || live < 1f : live < cfg.show_below;
        if (want) {
            lastSeen = now;
        } else if (now - lastSeen > holdMs) {
            return;
        }
        boolean spent = level < SPENT && now - lastHang < holdMs;
        boolean dim = cfg.blink_when_low && (spent ? now / 90 : now / 250) % 2 == 1
                && (spent || (hanging && level < cfg.low_threshold));
        boolean shake = cfg.blink_when_low && spent && now / 90 % 2 == 1;
        int s = Math.max(1, cfg.scale);
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int ax = (cfg.left_side ? cx - 12 - WheelLayout.SIZE * s : cx + 12) + (shake ? s : 0) + cfg.offset_x;
        int ay = Math.round(cy - 13.5f * s) + cfg.offset_y;
        refreshPalette(cfg);
        paint(cfg, spent, (dim ? DIM : 1f) * (float) cfg.opacity);
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) ax, (float) ay, 0f);
        pose.scale((float) s, (float) s, 1f);
        flush(g);
        pose.popPose();
    }

    private static void paint(ConfigData.Hud cfg, boolean spent, float scale) {
        java.util.Arrays.fill(GRID, 0);
        int[] lit = PALETTE[spent || level < cfg.low_threshold ? 2 : level < cfg.mid_threshold ? 1 : 0];
        WheelLayout w = WheelLayout.of(rings);
        int outline = argb(OUTLINE, OUTLINE_ALPHA * scale);
        for (int i = 0; i < w.outlineX.length; i++) {
            put(w.outlineX[i], w.outlineY[i], outline);
        }
        for (int i = 0; i < w.x.length; i++) {
            boolean on = spent || w.t[i] < ringFill(w.ring[i]);
            put(w.x[i], w.y[i], argb((on ? lit : STONE)[w.shade[i]], (on ? LIT_ALPHA : TRACK_ALPHA) * scale));
        }
        if (!cfg.show_pips) {
            return;
        }
        for (int px : pips == 2 ? PIPS_TWO : PIPS_ONE) {
            for (int dy = 0; dy < 5; dy++) {
                for (int dx = 0; dx < 5; dx++) {
                    boolean edge = dx == 0 || dx == 4 || dy == 0 || dy == 4;
                    int rgb = edge ? OUTLINE : (dx == 1 || dy == 1) ? GOLD[1] : (dx == 3 || dy == 3) ? GOLD[2] : GOLD[0];
                    put(px + dx, PIP_Y + dy, argb(rgb, LIT_ALPHA * scale));
                }
            }
        }
    }

    private static void flush(GuiGraphics g) {
        for (int row = 0; row < GRID_H; row++) {
            int x = 0;
            while (x < GRID_W) {
                int color = GRID[row * GRID_W + x];
                if (color == 0) {
                    x++;
                    continue;
                }
                int end = x + 1;
                while (end < GRID_W && GRID[row * GRID_W + end] == color) {
                    end++;
                }
                g.fill(x - 1, row - 1, end - 1, row, color);
                x = end;
            }
        }
    }

    private static void put(int x, int y, int color) {
        GRID[(y + 1) * GRID_W + x + 1] = color;
    }

    private static float ringFill(int ring) {
        return Math.min(1f, Math.max(0f, (level - (rings - 1 - ring) / (float) rings) * rings));
    }

    private static void refreshPalette(ConfigData.Hud cfg) {
        String key = cfg.high_color + "|" + cfg.mid_color + "|" + cfg.low_color;
        if (key.equals(paletteKey)) {
            return;
        }
        paletteKey = key;
        PALETTE[0] = shades(parse(cfg.high_color, DEFAULT_HIGH));
        PALETTE[1] = shades(parse(cfg.mid_color, DEFAULT_MID));
        PALETTE[2] = shades(parse(cfg.low_color, DEFAULT_LOW));
    }

    private static int parse(String hex, int fallback) {
        String s = hex == null ? "" : hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int[] shades(int rgb) {
        return new int[]{rgb, blend(rgb, 0xFFFFFF, 0.45f), blend(rgb, 0x000000, 0.5f)};
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int gr = Math.round((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return r << 16 | gr << 8 | b;
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(Math.min(1f, Math.max(0f, alpha)) * 255f) << 24) | rgb;
    }
}
