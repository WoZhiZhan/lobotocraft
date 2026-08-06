package com.wzz.lobotocraft.work;

import java.util.Locale;

public final class WorkFailureDamagePolicy {
    private WorkFailureDamagePolicy() {
    }

    public static String resolveDamageType(String abnormalityDamageType, WorkType workType) {
        if (abnormalityDamageType != null) {
            String normalized = abnormalityDamageType.toLowerCase(Locale.ROOT);
            if (normalized.equals("white") || normalized.equals("red")
                    || normalized.equals("black") || normalized.equals("blue")) {
                return normalized;
            }
        }
        return workType == null ? "white" : workType.getDamageType();
    }
}
