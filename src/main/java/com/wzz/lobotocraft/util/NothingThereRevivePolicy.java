package com.wzz.lobotocraft.util;

/** Server-side rules for the Nothing There fatal-hit revival. */
public final class NothingThereRevivePolicy {
    public static final long COOLDOWN_MILLIS = 90_000L;
    public static final float RESTORED_HEALTH_RATIO = 0.25F;

    private NothingThereRevivePolicy() {
    }

    public static boolean isLethal(float damage, float health) {
        return Float.isFinite(damage) ? damage >= health : damage > 0.0F;
    }

    public static boolean isCooldownActive(long nowMillis, long cooldownEndMillis) {
        return cooldownEndMillis > nowMillis;
    }

    public static float restoredHealth(float maxHealth) {
        return Math.max(1.0F, maxHealth * RESTORED_HEALTH_RATIO);
    }
}
