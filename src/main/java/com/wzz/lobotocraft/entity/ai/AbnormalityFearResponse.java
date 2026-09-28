package com.wzz.lobotocraft.entity.ai;

/** Fear levels map to 0%, 0%, 10%, 30%, 60%, and 100% maximum mental value. */
public record AbnormalityFearResponse(int level, boolean forcePanic) {
    public AbnormalityFearResponse {
        level = Math.max(0, Math.min(5, level));
    }

    public static AbnormalityFearResponse level(int level) {
        return new AbnormalityFearResponse(level, false);
    }

    public static AbnormalityFearResponse forcedPanic() {
        return new AbnormalityFearResponse(5, true);
    }

    public float maximumMentalFraction() {
        return switch (level) {
            case 2 -> 0.10F;
            case 3 -> 0.30F;
            case 4 -> 0.60F;
            case 5 -> 1.0F;
            default -> 0.0F;
        };
    }
}
