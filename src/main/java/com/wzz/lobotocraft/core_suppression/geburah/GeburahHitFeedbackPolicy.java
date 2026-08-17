package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.util.Mth;

public final class GeburahHitFeedbackPolicy {
    private GeburahHitFeedbackPolicy() {
    }

    public static float severity(float actualHealthLoss, float maxHealth,
                                 float attackIntensity) {
        if (!Float.isFinite(actualHealthLoss) || actualHealthLoss <= 0.0F
                || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) return 0.0F;
        float healthRatio = Mth.clamp(actualHealthLoss / maxHealth, 0.0F, 1.0F);
        float damageResponse = Mth.sqrt(Mth.clamp(healthRatio / 0.35F, 0.0F, 1.0F));
        float attackWeight = Float.isFinite(attackIntensity)
                ? Mth.clamp(attackIntensity, 0.0F, 1.25F) : 0.0F;
        return Mth.clamp(0.1F + damageResponse * 0.82F + attackWeight * 0.18F,
                0.12F, 1.25F);
    }
}
