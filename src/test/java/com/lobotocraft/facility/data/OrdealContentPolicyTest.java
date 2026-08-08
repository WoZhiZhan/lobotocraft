package com.lobotocraft.facility.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdealContentPolicyTest {
    @Test
    void duskAndMidnightAreExplicitlyMarkedUnfinished() {
        assertFalse(OrdealContentPolicy.isUnfinished(OrdealTier.NONE));
        assertFalse(OrdealContentPolicy.isUnfinished(OrdealTier.DAWN));
        assertFalse(OrdealContentPolicy.isUnfinished(OrdealTier.NOON));
        assertTrue(OrdealContentPolicy.isUnfinished(OrdealTier.DUSK));
        assertTrue(OrdealContentPolicy.isUnfinished(OrdealTier.MIDNIGHT));
    }
}
