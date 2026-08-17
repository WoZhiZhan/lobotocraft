package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahPursuitPolicyTest {
    @Test
    void usesElevatorOnlyForIdleCrossFloorPursuit() {
        assertTrue(GeburahPursuitPolicy.shouldUseElevator(
                20.0D, 30.0D, true, false));
        assertFalse(GeburahPursuitPolicy.shouldUseElevator(
                20.0D, 25.0D, true, false));
        assertFalse(GeburahPursuitPolicy.shouldUseElevator(
                20.0D, 30.0D, false, false));
        assertFalse(GeburahPursuitPolicy.shouldUseElevator(
                20.0D, 30.0D, true, true));
    }
}
