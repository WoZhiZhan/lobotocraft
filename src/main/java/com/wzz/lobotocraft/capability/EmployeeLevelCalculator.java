package com.wzz.lobotocraft.capability;

/** Calculates employee levels using Lobotomy Corporation's AgentModel rules. */
public final class EmployeeLevelCalculator {
    private EmployeeLevelCalculator() {
    }

    /** Converts a raw attribute value to its I-V attribute level. */
    public static int statLevel(int value) {
        if (value < 30) return 1;
        if (value < 45) return 2;
        if (value < 65) return 3;
        if (value < 85) return 4;
        return 5;
    }

    /** Original customization UI represents a stat of 100 or more as EX. */
    public static int displayedStatLevel(int value) {
        return value >= 100 ? 6 : statLevel(value);
    }

    public static String statGrade(int value) {
        return levelGrade(displayedStatLevel(value));
    }

    /** Formats an already-calculated I-V/EX level. */
    public static String levelGrade(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "EX";
            default -> "I";
        };
    }

    /** EX requirements inspect the raw stat; ordinary requirements use the I-V level. */
    public static int levelForEquipmentRequirement(int rawStat, int ordinaryLevel,
                                                   int requiredLevel) {
        return requiredLevel >= 6 ? displayedStatLevel(rawStat) : ordinaryLevel;
    }

    /** Calculates the employee level from four already-converted levels. */
    public static int fromStatLevels(int fortitude, int prudence,
                                     int temperance, int justice) {
        int sum = clampLevel(fortitude) + clampLevel(prudence)
                + clampLevel(temperance) + clampLevel(justice);
        if (sum < 6) return 1;
        if (sum < 9) return 2;
        if (sum < 12) return 3;
        if (sum < 16) return 4;
        return 5;
    }

    /** Calculates the employee level directly from raw attribute values. */
    public static int fromStatValues(int fortitude, int prudence,
                                     int temperance, int justice) {
        return fromStatLevels(statLevel(fortitude), statLevel(prudence),
                statLevel(temperance), statLevel(justice));
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }
}
