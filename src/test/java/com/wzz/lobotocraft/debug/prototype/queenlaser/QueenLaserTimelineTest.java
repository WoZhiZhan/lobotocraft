package com.wzz.lobotocraft.debug.prototype.queenlaser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueenLaserTimelineTest {
    @Test
    void castingDurationIsHalfOfTheOriginalAnimation() {
        assertEquals(134, QueenLaserTimeline.CAST_TICKS);
    }

    @Test
    void entitySpecificCastingDurationControlsIgnition() {
        assertEquals(0.5F, QueenLaserTimeline.castProgress(45.0F, 90));
        assertEquals(0.0F, QueenLaserTimeline.fireProgress(64.0F, 65, 265));
        assertTrue(QueenLaserTimeline.fireProgress(66.0F, 65, 265) > 0.0F);
    }

    @Test
    void mainCircleLayersExpandInSourceOrder() {
        float duringFirstLayer = 0.520F;
        assertTrue(QueenLaserTimeline.mainCircleLayerScale(duringFirstLayer, 0)
                > QueenLaserTimeline.mainCircleLayerScale(duringFirstLayer, 1));
        assertEquals(0.0F, QueenLaserTimeline.mainCircleLayerScale(duringFirstLayer, 2));
        assertEquals(0.0F, QueenLaserTimeline.mainCircleLayerScale(duringFirstLayer, 3));
    }

    @Test
    void circlePersistsThroughFiringAndFadesOnlyAtTheEnd() {
        assertTrue(QueenLaserTimeline.mainCircleAlpha(1.0F, 0.80F) > 0.99F);
        assertTrue(QueenLaserTimeline.mainCircleAlpha(1.0F, 0.94F) < 1.0F);
        assertEquals(0.0F, QueenLaserTimeline.mainCircleAlpha(1.0F, 1.0F));
    }
}
