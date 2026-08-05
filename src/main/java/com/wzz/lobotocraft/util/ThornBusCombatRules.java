package com.wzz.lobotocraft.util;

/** Deterministic combat constants shared by the Thorn Bus entity and its E.G.O. */
public final class ThornBusCombatRules {
    private static final int[] ATTACK_HIT_TICKS = {3, 6, 10};

    private ThornBusCombatRules() {
    }

    public static int attackHitCount() {
        return ATTACK_HIT_TICKS.length;
    }

    public static int attackHitTick(int index) {
        return ATTACK_HIT_TICKS[index];
    }

    public static DamageBand damageBandForTemperance(int temperanceLevel) {
        if (temperanceLevel <= 3) return new DamageBand(9, 24);
        if (temperanceLevel == 4) return new DamageBand(3, 8);
        return new DamageBand(1, 3);
    }

    public static float scaleBlackDamage(int baseDamage, float damageMultiplier) {
        if (baseDamage < 1 || baseDamage > 4) {
            throw new IllegalArgumentException("baseDamage must be between 1 and 4");
        }
        return baseDamage * damageMultiplier;
    }

    public record DamageBand(int minimum, int maximum) {
    }
}
