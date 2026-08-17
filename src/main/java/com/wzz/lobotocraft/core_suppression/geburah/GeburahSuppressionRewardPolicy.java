package com.wzz.lobotocraft.core_suppression.geburah;

/** Stable values and pure calculations for rewards unlocked by Geburah suppression. */
public final class GeburahSuppressionRewardPolicy {
    public static final float COMBAT_DAMAGE_MULTIPLIER = 1.15F;
    public static final int AI_EGO_EXTRA_RESEARCH = 1;
    public static final int AI_EGO_ABSOLUTE_CAP = 5;

    public static final String PLAYER_EGO_ID = "blood_mist";
    public static final String PLAYER_EGO_NAME = "血雾";
    public static final boolean PLAYER_EGO_IMPLEMENTED = false;

    private GeburahSuppressionRewardPolicy() {
    }

    public static int aiResearchMaximum(int baseMaximum, boolean rewardUnlocked) {
        int cappedBase = Math.max(1, Math.min(AI_EGO_ABSOLUTE_CAP, baseMaximum));
        return rewardUnlocked
                ? Math.min(AI_EGO_ABSOLUTE_CAP, cappedBase + AI_EGO_EXTRA_RESEARCH)
                : cappedBase;
    }

    public static float combatDamage(float amount, boolean rewardUnlocked,
                                     boolean eligibleTarget) {
        if (!Float.isFinite(amount) || amount <= 0.0F) return amount;
        return rewardUnlocked && eligibleTarget ? amount * COMBAT_DAMAGE_MULTIPLIER : amount;
    }
}
