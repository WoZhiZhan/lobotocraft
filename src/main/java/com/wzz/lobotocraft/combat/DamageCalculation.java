package com.wzz.lobotocraft.combat;

/** Color/channel arithmetic only. Values are pre-vanilla-armor, NOT observed health loss. */
public final class DamageCalculation {
    private DamageCalculation() {}

    /** PALE is canonical here; the persisted/network damage registry still uses blue. */
    public enum Color { RED, WHITE, BLACK, PALE, OTHER }

    public record Input(Color color, float amount, double resistance, boolean mentalTarget,
                        float incomingMultiplier, float paleExtra, float cap,
                        boolean whiteRecovery, boolean blackMentalShield) {}

    public record Result(float healthDamage, float mentalDamage,
                         float healthRecovery, float mentalRecovery) {
        public boolean hasHealthDamage() { return healthDamage > 0; }
    }

    public static Result resolve(Input in) {
        float amount = positive(in.amount);
        double resistance = Double.isFinite(in.resistance) ? in.resistance : 1;
        float scaled = (float) Math.min(Float.MAX_VALUE,
                amount * Math.abs(resistance) * (double) positive(in.incomingMultiplier));
        boolean white = in.mentalTarget && in.color == Color.WHITE;
        boolean black = in.mentalTarget && in.color == Color.BLACK;
        if (resistance < 0) {
            // Absorption is recovery, not negative damage. Do not run damage procs.
            return new Result(0, 0, white ? 0 : scaled, white || black ? scaled : 0);
        }
        if (resistance == 0 || amount == 0) return new Result(0, 0, 0, 0);
        float cap = Float.isFinite(in.cap) ? Math.max(0, in.cap) : Float.MAX_VALUE;
        float base = Math.min(scaled, cap);
        if (white) return in.whiteRecovery
                ? new Result(0, 0, 0, base) : new Result(0, base, 0, 0);
        float health = (float) Math.min(cap, (double) base + (in.color == Color.PALE ? positive(in.paleExtra) : 0));
        return new Result(health, black && !in.blackMentalShield ? base : 0, 0, 0);
    }

    public static float positive(float amount) {
        return Float.isFinite(amount) ? Math.max(0, amount) : 0;
    }
}
