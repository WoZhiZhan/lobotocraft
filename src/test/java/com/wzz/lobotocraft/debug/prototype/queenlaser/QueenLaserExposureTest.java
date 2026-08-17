package com.wzz.lobotocraft.debug.prototype.queenlaser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueenLaserExposureTest {
    @Test
    void exposureCurveIsMonotonicAndBounded() {
        float low = QueenLaserExposure.shadeAlpha(0.15F);
        float medium = QueenLaserExposure.shadeAlpha(0.50F);
        float high = QueenLaserExposure.shadeAlpha(1.0F);

        assertTrue(low > 0.0F);
        assertTrue(medium > low);
        assertTrue(high > medium);
        assertTrue(high <= QueenLaserExposure.MAX_SHADE);
        assertTrue(high >= 0.80F);
    }

    @Test
    void noVisibleLaserLeavesTheSceneUntouched() {
        assertEquals(0.0F, QueenLaserExposure.shadeAlpha(0.0F));
    }

    @Test
    void laserPixelsCutAContinuousHoleInTheDarkeningMask() {
        float outside = QueenLaserExposure.maskedShadeAlpha(1.0F, 0.0F);
        float transition = QueenLaserExposure.maskedShadeAlpha(1.0F, 0.5F);
        float laser = QueenLaserExposure.maskedShadeAlpha(1.0F, 1.0F);

        assertTrue(outside >= 0.80F);
        assertTrue(transition > 0.0F);
        assertTrue(transition < outside);
        assertEquals(0.0F, laser);
    }

    @Test
    void maskInputsAreClampedAndCannotMakeTheSceneBrighterOrDarkerThanTheCap() {
        assertEquals(QueenLaserExposure.shadeAlpha(1.0F),
                QueenLaserExposure.maskedShadeAlpha(1.0F, -1.0F));
        assertEquals(0.0F, QueenLaserExposure.maskedShadeAlpha(1.0F, 2.0F));
    }
}
