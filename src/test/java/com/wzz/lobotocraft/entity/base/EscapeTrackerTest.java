package com.wzz.lobotocraft.entity.base;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EscapeTrackerTest {
    @Test
    void stopRemovesOnlyExactUuidAndIsIdempotent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<UUID, RiskLevel> escapes = new HashMap<>();
        escapes.put(first, RiskLevel.ALEPH);
        escapes.put(second, RiskLevel.ALEPH);

        assertFalse(EscapeTracker.removeExact(escapes, UUID.randomUUID()));
        assertTrue(escapes.containsKey(first));
        assertTrue(escapes.containsKey(second));

        assertTrue(EscapeTracker.removeExact(escapes, first));
        assertFalse(EscapeTracker.removeExact(escapes, first));
        assertFalse(escapes.containsKey(first));
        assertTrue(escapes.containsKey(second));
    }

    @Test
    void rebuiltAlertStateCanBeSentWithoutAnotherEscapeTransition() {
        Map<UUID, RiskLevel> rebuilt = new HashMap<>();
        assertEquals(-1, EscapeTracker.calculateAlertLevel(rebuilt));
        rebuilt.put(UUID.randomUUID(), RiskLevel.ZAYIN);
        assertEquals(-1, EscapeTracker.calculateAlertLevel(rebuilt));
        rebuilt.put(UUID.randomUUID(), RiskLevel.HE);
        assertEquals(0, EscapeTracker.calculateAlertLevel(rebuilt));
        rebuilt.put(UUID.randomUUID(), RiskLevel.WAW);
        assertEquals(2, EscapeTracker.calculateAlertLevel(rebuilt));
    }

    @Test
    void rebuiltScoreUsesOpenedDepartmentScaling() {
        Map<UUID, RiskLevel> rebuilt = new HashMap<>();
        rebuilt.put(UUID.randomUUID(), RiskLevel.ALEPH);
        assertEquals(75.0F, EscapeTracker.calculateScore(rebuilt, 1));
        assertEquals(15.0F, EscapeTracker.calculateScore(rebuilt, 5));
        assertEquals(1, EscapeTracker.calculateAlertLevel(rebuilt, 1));
        assertEquals(0, EscapeTracker.calculateAlertLevel(rebuilt, 5));
    }
}
