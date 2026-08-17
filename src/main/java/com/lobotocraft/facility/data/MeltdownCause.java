package com.lobotocraft.facility.data;

/** Origin of a timed containment-unit meltdown. */
public enum MeltdownCause {
    QLIPHOTH_OVERLOAD(false),
    APOCALYPSE_BIRD_EGG(false),
    DEBUG(false),
    BINAH(true),
    LEGACY_OR_UNKNOWN(true);

    private final boolean requiresCoreSuppression;

    MeltdownCause(boolean requiresCoreSuppression) {
        this.requiresCoreSuppression = requiresCoreSuppression;
    }

    public boolean requiresCoreSuppression() {
        return requiresCoreSuppression;
    }

    public static MeltdownCause read(String value) {
        if (value == null || value.isBlank()) return LEGACY_OR_UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LEGACY_OR_UNKNOWN;
        }
    }
}
