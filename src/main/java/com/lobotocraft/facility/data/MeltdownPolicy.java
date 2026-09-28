package com.lobotocraft.facility.data;

import java.util.Locale;

/** Shared policy boundary between normal days and core-suppression days. */
public final class MeltdownPolicy {
    public static final String DARK_SKADI_CODE = "S-03-07";
    public static final String WE_CAN_CHANGE_ANYTHING_CODE = "T-09-85";

    private MeltdownPolicy() {
    }

    public static boolean canActivate(boolean ignoredCoreSuppressionActive, boolean ordealTriggered) {
        return !ordealTriggered;
    }

    public static boolean normalMeltdownEnabled(int day) {
        return day >= 6;
    }

    public static int normalMeltdownCapacity(int facilityPlayerCount) {
        return Math.max(0, facilityPlayerCount) + 2;
    }

    public static int stageNotificationType(boolean ignoredCoreSuppressionActive,
                                            boolean ordealTriggered) {
        if (ordealTriggered) return 1;
        return 2;
    }

    public static boolean countsForFacilityProgress(boolean forcedEnd) {
        return !forcedEnd;
    }

    /** A work session used to resolve an active meltdown is not new progress. */
    public static boolean advancesGaugeForWork(boolean activeMeltdown) {
        return !activeMeltdown;
    }

    /** Every activation that adds at least one target is a fresh alarm batch. */
    public static boolean shouldAnnounceActivation(int previousActiveCount, int activatedCount) {
        return previousActiveCount >= 0 && activatedCount > 0;
    }

    /**
     * Mod equivalent of CreatureModel.GetMaxWorkCount() != 0. Tool identity is
     * deliberately not a blanket exclusion; only missing normal-work capacity
     * and temporary encounter bodies keep an object out of the workable pool.
     */
    public static boolean hasNormalWorkCapacity(int maxPeOutput,
                                                boolean temporaryEncounterEntity) {
        return maxPeOutput > 0 && !temporaryEncounterEntity;
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
