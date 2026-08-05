package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.IEmployeeStats;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Original Lobotomy Corporation's floating attribute experience calculation. */
final class WorkAttributeExperience {
    private static final String EXPERIENCE_TAG = "LobotocraftAttributeExperience";

    private WorkAttributeExperience() {
    }

    static int calculateIncrease(ServerPlayer player, IEmployeeStats stats, String riskLevel,
                                 WorkType workType, WorkManager.WorkSession session) {
        int successfulPeBoxes = session == null ? 0 : session.successCount;
        stats.incrementWorkCount(riskLevel);
        if (successfulPeBoxes <= 0) return 0;

        float healthRatio = ratio(player.getHealth(), session.startingHealth);
        float mentalRatio = ratio(MentalValueUtil.getMentalValue(player), session.startingMental);
        int statLevel = workedStatLevel(stats, workType);
        float earned = successfulPeBoxes
                * levelRiskFactor(statLevel, riskLevel)
                * injuryFactor(workType, healthRatio, mentalRatio)
                * (CoreSuppressionManager.hasReward(player, CoreSuppressionType.HOD) ? 2.0F : 1.2F)
                * (workType == WorkType.REPRESSION ? 0.5F : 1.0F);
        return addExperience(player, experienceKey(workType), earned);
    }

    private static float ratio(float current, float initial) {
        return initial <= 0.0F ? 1.0F : Math.max(0.0F, current / initial);
    }

    private static int workedStatLevel(IEmployeeStats stats, WorkType type) {
        return switch (type) {
            case INSTINCT -> stats.getFortitudeLevel();
            case INSIGHT -> stats.getPrudenceLevel();
            case ATTACHMENT -> stats.getTemperanceLevel();
            case REPRESSION -> stats.getJusticeLevel();
        };
    }

    private static float levelRiskFactor(int statLevel, String riskLevel) {
        int difference = statLevel - riskValue(riskLevel);
        float riskFactor = switch (difference) {
            case -3 -> 1.4F;
            case -2 -> 1.2F;
            case -1, 0 -> 1.0F;
            case 1 -> 0.8F;
            case 2 -> 0.6F;
            case 3 -> 0.4F;
            case 4 -> 0.2F;
            default -> 1.0F;
        };
        float levelFactor = switch (statLevel) {
            case 1 -> 0.60F;
            case 2 -> 0.55F;
            case 3 -> 0.50F;
            case 4 -> 0.45F;
            case 5 -> 0.40F;
            default -> 0.60F;
        };
        return riskFactor * levelFactor;
    }

    private static int riskValue(String riskLevel) {
        return switch (riskLevel) {
            case "ZAYIN" -> 1;
            case "TETH" -> 2;
            case "HE" -> 3;
            case "WAW" -> 4;
            case "ALEPH" -> 5;
            default -> 1;
        };
    }

    private static float injuryFactor(WorkType type, float healthRatio, float mentalRatio) {
        return switch (type) {
            case INSTINCT -> damageFactor(healthRatio);
            case INSIGHT -> damageFactor(mentalRatio);
            case ATTACHMENT -> damageFactor((healthRatio + mentalRatio) / 2.0F);
            case REPRESSION -> 1.0F;
        };
    }

    private static float damageFactor(float ratio) {
        if (ratio >= 0.9F) return 0.4F;
        if (ratio >= 0.8F) return 0.6F;
        if (ratio >= 0.7F) return 0.8F;
        if (ratio > 0.2F) return 1.0F;
        if (ratio > 0.1F) return 1.3F;
        return 1.5F;
    }

    private static int addExperience(ServerPlayer player, String key, float earned) {
        if (earned <= 0.0F) return 0;
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag experience = persistentData.getCompound(EXPERIENCE_TAG);
        float total = Math.max(0.0F, experience.getFloat(key)) + earned;
        int applied = Math.max(0, experience.getInt(key + "Applied"));
        int whole = Math.max(0, Math.round(total) - applied);
        experience.putFloat(key, total);
        experience.putInt(key + "Applied", applied + whole);
        persistentData.put(EXPERIENCE_TAG, experience);
        return whole;
    }

    private static String experienceKey(WorkType type) {
        return switch (type) {
            case INSTINCT -> "Fortitude";
            case INSIGHT -> "Prudence";
            case ATTACHMENT -> "Temperance";
            case REPRESSION -> "Justice";
        };
    }
}
