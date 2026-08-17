package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahComboTacticsTest {
    @Test
    void fleeingDistantTargetSelectsStab() {
        GeburahMove selected = GeburahComboTactics.chooseNext(
                GeburahMove.SLASH,
                new GeburahComboTactics.Snapshot(7.0D, 0.3D, 0.35D, 1, true),
                bound -> 0);

        assertEquals(GeburahMove.STAB, selected);
    }

    @Test
    void lateralTargetSelectsSlashAndClusterSelectsVerticalSlash() {
        assertEquals(GeburahMove.SLASH, GeburahComboTactics.chooseNext(
                GeburahMove.STAB,
                new GeburahComboTactics.Snapshot(3.2D, 2.7D, 0.0D, 1, true),
                bound -> 0));
        assertEquals(GeburahMove.VERTICAL_SLASH, GeburahComboTactics.chooseNext(
                GeburahMove.SLASH,
                new GeburahComboTactics.Snapshot(3.4D, 0.2D, 0.0D, 4, true),
                bound -> 0));
    }

    @Test
    void nextMoveNeverRepeatsPreviousMove() {
        for (GeburahMove previous : GeburahMove.values()) {
            GeburahMove selected = GeburahComboTactics.chooseNext(previous,
                    new GeburahComboTactics.Snapshot(4.0D, 1.0D, 0.1D, 2, true),
                    bound -> 0);
            assertNotEquals(previous, selected);
        }
    }

    @Test
    void pursuitUsesBoundedStepAndTurn() {
        assertEquals(0.0D, GeburahPursuitPolicy.stepDistance(3.0D, true));
        assertEquals(3.0D, GeburahPursuitPolicy.stepDistance(8.0D, true));
        assertEquals(2.25D, GeburahPursuitPolicy.stepDistance(8.0D, false));
        assertEquals(55.0F, GeburahPursuitPolicy.turnToward(0.0F, 120.0F, false));
        assertEquals(-75.0F, GeburahPursuitPolicy.turnToward(0.0F, -120.0F, true));
        assertTrue(Math.abs(GeburahPursuitPolicy.turnToward(170.0F, -170.0F, true)
                - -170.0F) < 0.01F);
    }
}
