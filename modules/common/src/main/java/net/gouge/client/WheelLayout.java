package net.gouge.client;

import java.util.ArrayList;
import java.util.List;

final class WheelLayout {

    static final int SIZE = 27;

    private static final double CENTER = 13.5;
    private static final double[][] RINGS = {{9.5, 12.5}, {5.5, 8.0}, {1.5, 4.0}};
    private static final WheelLayout[] CACHE = new WheelLayout[RINGS.length + 1];

    final int[] x;
    final int[] y;
    final int[] ring;
    final int[] shade;
    final float[] t;
    final int[] outlineX;
    final int[] outlineY;

    static WheelLayout of(int rings) {
        int n = Math.max(1, Math.min(RINGS.length, rings));
        if (CACHE[n] == null) {
            CACHE[n] = new WheelLayout(n);
        }
        return CACHE[n];
    }

    private WheelLayout(int rings) {
        int[][] ringAt = new int[SIZE][SIZE];
        double[][] angleAt = new double[SIZE][SIZE];
        List<int[]> cells = new ArrayList<>();
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double dx = px + 0.5 - CENTER;
                double dy = py + 0.5 - CENTER;
                double d = Math.hypot(dx, dy);
                int ri = -1;
                for (int i = 0; i < rings; i++) {
                    if (d >= RINGS[i][0] && d < RINGS[i][1]) {
                        ri = i;
                        break;
                    }
                }
                ringAt[py][px] = ri;
                if (ri < 0) {
                    continue;
                }
                double a = Math.atan2(dx, -dy);
                if (a < 0) {
                    a += Math.PI * 2;
                }
                angleAt[py][px] = a / (Math.PI * 2);
                cells.add(new int[]{px, py});
            }
        }
        int n = cells.size();
        x = new int[n];
        y = new int[n];
        ring = new int[n];
        shade = new int[n];
        t = new float[n];
        for (int i = 0; i < n; i++) {
            int px = cells.get(i)[0];
            int py = cells.get(i)[1];
            x[i] = px;
            y[i] = py;
            ring[i] = ringAt[py][px];
            t[i] = (float) angleAt[py][px];
            if (!has(ringAt, px - 1, py) || !has(ringAt, px, py - 1)) {
                shade[i] = 1;
            } else if (!has(ringAt, px + 1, py) || !has(ringAt, px, py + 1)) {
                shade[i] = 2;
            }
        }
        List<int[]> outline = new ArrayList<>();
        boolean[][] seen = new boolean[SIZE + 2][SIZE + 2];
        for (int[] c : cells) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int ox = -1; ox <= 1; ox++) {
                    int nx = c[0] + ox;
                    int ny = c[1] + oy;
                    if (!has(ringAt, nx, ny) && !seen[ny + 1][nx + 1]) {
                        seen[ny + 1][nx + 1] = true;
                        outline.add(new int[]{nx, ny});
                    }
                }
            }
        }
        outlineX = new int[outline.size()];
        outlineY = new int[outline.size()];
        for (int i = 0; i < outline.size(); i++) {
            outlineX[i] = outline.get(i)[0];
            outlineY[i] = outline.get(i)[1];
        }
    }

    private static boolean has(int[][] ringAt, int px, int py) {
        return px >= 0 && px < SIZE && py >= 0 && py < SIZE && ringAt[py][px] >= 0;
    }
}
