package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahActionTimingTest {
    @Test
    void egoSpeedShortensEveryActionStageAndKeepsHitInsideStrike() {
        GeburahActionTiming timing = GeburahActionTiming.forPhase(true);

        assertEquals(1.60D, timing.animationSpeed(), 0.001D);
        assertTrue(timing.windupTicks() < GeburahMove.WINDUP_TICKS);
        assertTrue(timing.recoveryTicks(GeburahMove.STAB) < GeburahMove.STAB.recoveryTicks());
        assertEquals(9, timing.strikeTicks(GeburahMove.SLASH));
        assertEquals(3, timing.hitTick(GeburahMove.SLASH));
        assertTrue(timing.hitTick(GeburahMove.SLASH) < timing.strikeTicks(GeburahMove.SLASH));
    }

    @Test
    void normalPhaseRetainsAnimationCalibratedTickDurations() {
        GeburahActionTiming timing = GeburahActionTiming.forPhase(false);

        assertEquals(1.0D, timing.animationSpeed(), 0.001D);
        assertEquals(GeburahMove.WINDUP_TICKS, timing.windupTicks());
        assertEquals(GeburahMove.VERTICAL_SLASH.strikeTicks(false),
                timing.strikeTicks(GeburahMove.VERTICAL_SLASH));
        assertEquals(GeburahMove.VERTICAL_SLASH.hitTick(false),
                timing.hitTick(GeburahMove.VERTICAL_SLASH));
    }
}
