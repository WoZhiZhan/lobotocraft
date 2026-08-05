package com.wzz.lobotocraft.employee;

/** Pure range-band rules for AI employees using ranged E.G.O weapons. */
final class RangedCombatPolicy {
    enum State { RETREAT, HOLD, APPROACH }

    private RangedCombatPolicy() {
    }

    static Profile normalize(double attackRange, double minimumSpacing,
                             double preferredSpacing, double maximumSpacing) {
        double attack = positiveOr(attackRange, 1.0D);
        double maximum = clamp(positiveOr(maximumSpacing, attack), 1.0D, attack);
        double minimum = clamp(positiveOr(minimumSpacing, 1.0D), 1.0D, maximum);
        double preferred = clamp(positiveOr(preferredSpacing, maximum), minimum, maximum);
        return new Profile(attack, minimum, preferred, maximum);
    }

    static State state(double distance, boolean hasLineOfSight, Profile profile) {
        if (distance < profile.minimumSpacing()) return State.RETREAT;
        if (distance > profile.maximumSpacing() || !hasLineOfSight) return State.APPROACH;
        return State.HOLD;
    }

    private static double positiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Profile(double attackRange, double minimumSpacing,
                   double preferredSpacing, double maximumSpacing) {
    }
}
