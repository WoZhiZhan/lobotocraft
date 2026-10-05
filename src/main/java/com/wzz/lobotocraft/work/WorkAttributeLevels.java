package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.IEmployeeStats;

/** Single attribute grades used by work probabilities, work UI and attribute experience. */
public record WorkAttributeLevels(int fortitude, int prudence, int temperance, int justice) {
    public static final WorkAttributeLevels LOWEST = new WorkAttributeLevels(1, 1, 1, 1);

    public WorkAttributeLevels {
        fortitude = clamp(fortitude);
        prudence = clamp(prudence);
        temperance = clamp(temperance);
        justice = clamp(justice);
    }

    public static WorkAttributeLevels from(IEmployeeStats stats) {
        return stats == null ? LOWEST : new WorkAttributeLevels(stats.getFortitudeLevel(),
                stats.getPrudenceLevel(), stats.getTemperanceLevel(), stats.getJusticeLevel());
    }

    public int levelFor(WorkType type) {
        return switch (type) {
            case INSTINCT -> fortitude;
            case INSIGHT -> prudence;
            case ATTACHMENT -> temperance;
            case REPRESSION -> justice;
        };
    }

    public int columnFor(WorkType type) {
        return levelFor(type) - 1;
    }

    private static int clamp(int grade) {
        return Math.max(1, Math.min(5, grade));
    }
}
