package com.wzz.lobotocraft.entity.base;

import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.entity.data.RiskLevel;

/** Pure policy for Lobotomy Corporation's facility emergency score. */
public final class EmergencyAlarmPolicy {
    public static final float MAX_SCORE = 100.0F;
    public static final int RECOVERY_BLOCK_TICKS = 30 * 20;
    public static final int RECOVERY_INTERVAL_TICKS = 5 * 20;

    private EmergencyAlarmPolicy() {
    }

    public static float riskScore(RiskLevel risk) {
        if (risk == null) return 0.0F;
        return switch (risk) {
            case ZAYIN -> 5.0F;
            case TETH -> 20.0F;
            case HE -> 40.0F;
            case WAW -> 60.0F;
            case ALEPH -> 75.0F;
        };
    }

    public static RiskLevel ordealRisk(OrdealTier tier) {
        if (tier == null) return null;
        return switch (tier) {
            case DAWN -> RiskLevel.TETH;
            case NOON -> RiskLevel.HE;
            case DUSK -> RiskLevel.WAW;
            case MIDNIGHT -> RiskLevel.ALEPH;
            case NONE -> null;
        };
    }

    /** Original events divide their risk score by the number of opened departments. */
    public static float eventScore(RiskLevel risk, int openedDepartments) {
        return riskScore(risk) / Math.max(1, openedDepartments);
    }

    /**
     * Wire/UI levels retain this mod's -1/0/1/2 representation:
     * normal/level I/level II/level III.
     */
    public static int alertLevel(float score) {
        float bounded = clampScore(score);
        if (bounded <= 10.0F) return -1;
        if (bounded <= 50.0F) return 0;
        if (bounded <= 80.0F) return 1;
        return 2;
    }

    public static float add(float score, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) return clampScore(score);
        return clampScore(clampScore(score) + amount);
    }

    /** Original recovery removes the alive/stage-start employee ratio every interval. */
    public static float recover(float score, float aliveRatio) {
        float ratio = Float.isFinite(aliveRatio)
                ? Math.max(0.0F, Math.min(1.0F, aliveRatio)) : 1.0F;
        return clampScore(clampScore(score) - ratio);
    }

    public static float clampScore(float score) {
        if (!Float.isFinite(score)) return score > 0.0F ? MAX_SCORE : 0.0F;
        return Math.max(0.0F, Math.min(MAX_SCORE, score));
    }
}
