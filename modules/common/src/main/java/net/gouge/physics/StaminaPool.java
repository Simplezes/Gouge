package net.gouge.physics;

public final class StaminaPool {

    private StaminaPool() {
    }

    public static double total(int baseTicks, boolean dual, double dualMultiplier) {
        double total = dual ? Math.round(baseTicks * dualMultiplier) : baseTicks;
        return Math.max(1.0, total);
    }

    public static float level(float from, double elapsedTicks, double totalTicks) {
        return (float) Math.min(1.0, Math.max(0.0, from - elapsedTicks / Math.max(1.0, totalTicks)));
    }

    public static float refill(float current, double refillSeconds) {
        if (refillSeconds <= 0) {
            return 1f;
        }
        return (float) Math.min(1.0, current + 1.0 / (refillSeconds * 20.0));
    }
}
