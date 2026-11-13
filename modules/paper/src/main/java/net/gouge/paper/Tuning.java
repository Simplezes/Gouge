package net.gouge.paper;

final class Tuning {
    private Tuning() {
    }

    static final double SOFT_MAX = 0.5, SOFT_MIN = 0.3, SOFT_SCALE = 0.13, H_DAMP = 0.5;
    static final double HARD_BASE = 0.90, HARD_SCALE = 0.03, HARD_MIN = 0.55, HARD_MAX = 0.90, HANG_LOCK = 0.08;
    static final int MANTLE_REGRAB = 20;
    static final int ASSIST_TICKS = 26;
}
