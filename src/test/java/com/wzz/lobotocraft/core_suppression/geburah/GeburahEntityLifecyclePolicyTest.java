package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahEntityLifecyclePolicyTest {
    private final UUID authoritativeUuid = UUID.randomUUID();

    @Test
    void keepsOnlyTheAuthoritativeEntityOfAnActiveGeburahEncounter() {
        assertFalse(GeburahEntityLifecyclePolicy.shouldDiscard(
                CoreSuppressionType.GEBURAH, true, authoritativeUuid, authoritativeUuid, true));
    }

    @Test
    void discardsEntityAfterReturningToANormalDay() {
        assertTrue(GeburahEntityLifecyclePolicy.shouldDiscard(
                null, true, authoritativeUuid, authoritativeUuid, true));
    }

    @Test
    void discardsStaleDuplicateAndWrongDimensionEntities() {
        assertTrue(GeburahEntityLifecyclePolicy.shouldDiscard(
                CoreSuppressionType.GEBURAH, false, authoritativeUuid, authoritativeUuid, true));
        assertTrue(GeburahEntityLifecyclePolicy.shouldDiscard(
                CoreSuppressionType.GEBURAH, true, authoritativeUuid, UUID.randomUUID(), true));
        assertTrue(GeburahEntityLifecyclePolicy.shouldDiscard(
                CoreSuppressionType.GEBURAH, true, authoritativeUuid, authoritativeUuid, false));
    }
}
