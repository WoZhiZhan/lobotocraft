package com.wzz.lobotocraft.capability;

/** Server-owned panic classification. The selected type is frozen until recovery. */
public enum PanicType {
    NONE,
    BERSERK,
    PRUDENCE;

    public static final float RECOVERY_RATIO = 0.5F;

    /** Uses effective maxima, including equipment and Curios attribute modifiers. */
    public static PanicType select(float effectiveMaxHealth, float effectiveMaxMental) {
        float health = sanitize(effectiveMaxHealth);
        float mental = sanitize(effectiveMaxMental);
        return health > mental ? BERSERK : PRUDENCE;
    }

    /** Keeps an active type stable, and clears it only after mental recovery. */
    public static PanicType resolve(PanicType current, boolean panicking,
                                    float effectiveMaxHealth, float effectiveMaxMental) {
        if (!panicking) return NONE;
        if (current != null && current != NONE) return current;
        return select(effectiveMaxHealth, effectiveMaxMental);
    }

    /** An active panic ends only after SP is strictly above half of its effective maximum. */
    public static boolean hasRecovered(float mentalValue, float effectiveMaxMental) {
        if (!Float.isFinite(mentalValue) || !Float.isFinite(effectiveMaxMental)
                || effectiveMaxMental <= 0.0F) return false;
        return mentalValue > effectiveMaxMental * RECOVERY_RATIO;
    }

    /** Low SP alone does not start panic; an existing type keeps it active during recovery. */
    public static boolean isActive(PanicType current, float mentalValue, float effectiveMaxMental) {
        if (!Float.isFinite(mentalValue) || mentalValue <= 0.0F) return true;
        return current != null && current != NONE
                && !hasRecovered(mentalValue, effectiveMaxMental);
    }

    public static PanicType byName(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }

    private static float sanitize(float value) {
        if (Float.isNaN(value) || value <= 0.0F) return 0.0F;
        return value == Float.POSITIVE_INFINITY ? Float.MAX_VALUE : value;
    }
}
