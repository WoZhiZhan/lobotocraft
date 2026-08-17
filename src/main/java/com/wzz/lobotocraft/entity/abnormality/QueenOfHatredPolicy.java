package com.wzz.lobotocraft.entity.abnormality;

import net.minecraft.util.Mth;

final class QueenOfHatredPolicy {
    static final int STAGE_DEATH_REQUIREMENT = 3;
    static final int HYSTERIC_RECOVERY_PE = 16;
    static final float HYSTERIC_WORK_PENALTY = 0.20F;

    private QueenOfHatredPolicy() {
    }

    static boolean shouldReduceCounterAtStageChange(int staffDeaths) {
        return Math.max(0, staffDeaths) < STAGE_DEATH_REQUIREMENT;
    }

    static boolean shouldBecomeHysteric(int staffDeaths, int staffAtSortieStart) {
        return staffAtSortieStart > 0
                && (long) Math.max(0, staffDeaths) * 5L >= staffAtSortieStart;
    }

    static boolean overcomesHysteria(int peOutput) {
        return peOutput >= HYSTERIC_RECOVERY_PE;
    }

    static float applyHystericWorkPenalty(float baseRate) {
        return Mth.clamp(baseRate - HYSTERIC_WORK_PENALTY, 0.0F, 1.0F);
    }
}
