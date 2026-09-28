package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahPursuitPolicyTest {
    @Test
    void egoAndNormalPursuitUseRaisedDistinctSpeeds() {
        assertEquals(0.24D, GeburahPursuitPolicy.movementSpeed(false), 0.0001D);
        assertEquals(0.54D, GeburahPursuitPolicy.movementSpeed(true), 0.0001D);
    }

    @Test
    void movementFacingUsesActualHorizontalTravelInEveryCardinalDirection() {
        assertEquals(-90.0F, GeburahPursuitPolicy.movementYaw(1.0D, 0.0D), 0.001F);
        assertEquals(90.0F, GeburahPursuitPolicy.movementYaw(-1.0D, 0.0D), 0.001F);
        assertEquals(0.0F, GeburahPursuitPolicy.movementYaw(0.0D, 1.0D), 0.001F);
        assertEquals(-180.0F, GeburahPursuitPolicy.movementYaw(0.0D, -1.0D), 0.001F);
    }

    @Test
    void movementFacingYieldsToCombatDirectionLock() {
        assertTrue(GeburahPursuitPolicy.shouldFaceMovement(false, 0.2D, 0.0D));
        assertFalse(GeburahPursuitPolicy.shouldFaceMovement(true, 0.2D, 0.0D));
        assertFalse(GeburahPursuitPolicy.shouldFaceMovement(false, 0.0D, 0.0D));
    }

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
