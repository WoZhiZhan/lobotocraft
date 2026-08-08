package com.wzz.lobotocraft.entity.abnormality;

/** Exact numeric pulse rules used by Lobotomy Corporation's IronMaiden script. */
final class IronMaidenGenerationPolicy {
    private static final double TICKS_PER_SECOND = 20.0D;

    private IronMaidenGenerationPolicy() {
    }

    static int intervalTicks(int completedPulses) {
        double seconds = Math.max(0.1D, 2.0D - 0.1D * Math.max(0, completedPulses));
        return Math.max(2, (int) Math.round(seconds * TICKS_PER_SECOND));
    }

    static Pulse nextPulse(int completedPulses) {
        int safeCompleted = Math.max(0, completedPulses);
        double damage = damageForPulse(safeCompleted);
        int nextCompleted = safeCompleted == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : safeCompleted + 1;
        double generatedEnergy = square(nextCompleted) / 110.0D;
        return new Pulse(nextCompleted, damage, generatedEnergy,
                intervalTicks(nextCompleted));
    }

    static double damageForPulse(int completedPulses) {
        return square(Math.max(0, completedPulses)) / 30.0D;
    }

    private static double square(int value) {
        double numeric = value;
        return numeric * numeric;
    }

    record Pulse(int completedPulses, double damage, double generatedEnergy,
                 int nextIntervalTicks) {
    }
}
