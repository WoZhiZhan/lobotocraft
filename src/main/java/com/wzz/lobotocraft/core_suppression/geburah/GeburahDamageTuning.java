package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahDamageTuning {
    public static final double DEFAULT_ABNORMALITY_DAMAGE_MULTIPLIER = 5.0D;
    public static final double DEFAULT_ABNORMALITY_ASSUMED_RED_RESISTANCE = 2.0D;
    private static final ThreadLocal<Integer> NEUTRAL_PLAYER_RED_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static volatile boolean abnormalityOverrideEnabled = true;
    private static volatile double abnormalityDamageMultiplier =
            DEFAULT_ABNORMALITY_DAMAGE_MULTIPLIER;
    private static volatile double abnormalityAssumedRedResistance =
            DEFAULT_ABNORMALITY_ASSUMED_RED_RESISTANCE;

    private GeburahDamageTuning() {
    }

    public static double effectiveRedMultiplier(boolean geburahAttack,
                                                boolean abnormalityTarget,
                                                double normalResistance) {
        if (!abnormalityOverrideEnabled || !geburahAttack || !abnormalityTarget) {
            return normalResistance;
        }
        return abnormalityDamageMultiplier * abnormalityAssumedRedResistance;
    }

    public static void configureAbnormalityOverride(boolean enabled,
                                                    double damageMultiplier,
                                                    double assumedRedResistance) {
        abnormalityOverrideEnabled = enabled;
        if (Double.isFinite(damageMultiplier) && damageMultiplier >= 0.0D) {
            abnormalityDamageMultiplier = damageMultiplier;
        }
        if (Double.isFinite(assumedRedResistance) && assumedRedResistance >= 0.0D) {
            abnormalityAssumedRedResistance = assumedRedResistance;
        }
    }

    public static boolean abnormalityOverrideEnabled() {
        return abnormalityOverrideEnabled;
    }

    public static void resetAbnormalityOverride() {
        configureAbnormalityOverride(true, DEFAULT_ABNORMALITY_DAMAGE_MULTIPLIER,
                DEFAULT_ABNORMALITY_ASSUMED_RED_RESISTANCE);
    }

    public static double effectivePlayerRedMultiplier(boolean geburahAttack,
                                                       double normalResistance) {
        return geburahAttack && NEUTRAL_PLAYER_RED_DEPTH.get() > 0
                ? 1.0D : normalResistance;
    }

    public static boolean shouldNeutralizePlayerRed(GeburahPhase phase) {
        return phase == GeburahPhase.EGO;
    }

    public static <T> T withNeutralPlayerRed(java.util.function.Supplier<T> action) {
        int previousDepth = NEUTRAL_PLAYER_RED_DEPTH.get();
        NEUTRAL_PLAYER_RED_DEPTH.set(previousDepth + 1);
        try {
            return action.get();
        } finally {
            if (previousDepth == 0) NEUTRAL_PLAYER_RED_DEPTH.remove();
            else NEUTRAL_PLAYER_RED_DEPTH.set(previousDepth);
        }
    }
}
