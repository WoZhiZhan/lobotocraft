package com.lobotocraft.facility.data;

import java.util.Locale;

/** Shared policy boundary between normal days and core-suppression days. */
public final class MeltdownPolicy {
    public static final String DARK_SKADI_CODE = "S-03-07";
    public static final String WE_CAN_CHANGE_ANYTHING_CODE = "T-09-85";

    private MeltdownPolicy() {
    }

    public static boolean canActivate(boolean coreSuppressionActive, boolean ordealTriggered) {
        return coreSuppressionActive && !ordealTriggered;
    }

    public static int stageNotificationType(boolean coreSuppressionActive, boolean ordealTriggered) {
        if (ordealTriggered) return 1;
        return coreSuppressionActive ? 2 : 0;
    }

    public static boolean countsForFacilityProgress(boolean forcedEnd) {
        return !forcedEnd;
    }

    /**
     * These containment units are immune to Qliphoth meltdowns. Codes are used
     * as the persistent identity so bindings made by older saves remain safe.
     */
    public static boolean isProtectedContainmentCode(String code) {
        if (code == null) return false;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return DARK_SKADI_CODE.equals(normalized)
            || WE_CAN_CHANGE_ANYTHING_CODE.equals(normalized);
    }

    public static boolean isProtectedTarget(String entityCode, String boundRoomCode,
                                              boolean protectedEntityType) {
        return protectedEntityType
            || isProtectedContainmentCode(entityCode)
            || isProtectedContainmentCode(boundRoomCode);
    }

    public static boolean canReceiveDirectMeltdown(String entityCode, String boundRoomCode,
                                                    boolean protectedEntityType) {
        return !isProtectedTarget(entityCode, boundRoomCode, protectedEntityType);
    }
}
