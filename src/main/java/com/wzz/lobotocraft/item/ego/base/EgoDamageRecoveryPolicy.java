package com.wzz.lobotocraft.item.ego.base;

public final class EgoDamageRecoveryPolicy {
    private EgoDamageRecoveryPolicy() {
    }

    public static float actualHealthLoss(float healthBefore, float healthAfter) {
        if (!Float.isFinite(healthBefore) || !Float.isFinite(healthAfter)) return 0.0F;
        return Math.max(0.0F, healthBefore - Math.max(0.0F, healthAfter));
    }

    public static float finalDamageToHealth(float currentHealth, float finalDamage) {
        if (!Float.isFinite(currentHealth) || !Float.isFinite(finalDamage)) return 0.0F;
        return Math.max(0.0F, Math.min(currentHealth, finalDamage));
    }
}
