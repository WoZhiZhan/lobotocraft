package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;

import java.util.UUID;

final class GeburahEntityLifecyclePolicy {
    private GeburahEntityLifecyclePolicy() {
    }

    static boolean shouldDiscard(CoreSuppressionType activeType, boolean encounterActive,
                                 UUID authoritativeEntityUuid, UUID loadedEntityUuid,
                                 boolean inFacilityDimension) {
        return activeType != CoreSuppressionType.GEBURAH
                || !encounterActive
                || authoritativeEntityUuid == null
                || !authoritativeEntityUuid.equals(loadedEntityUuid)
                || !inFacilityDimension;
    }
}
