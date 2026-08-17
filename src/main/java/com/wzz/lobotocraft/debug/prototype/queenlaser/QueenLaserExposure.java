package com.wzz.lobotocraft.debug.prototype.queenlaser;

import net.minecraft.util.Mth;

/** Safe exposure curve for the local-adaptation mask around a visible laser. */
final class QueenLaserExposure {
    static final float MAX_SHADE = 0.88F;

    private QueenLaserExposure() {
    }

    static float shadeAlpha(float exposure) {
        float safeExposure = Mth.clamp(exposure, 0.0F, 1.0F);
        float adapted = 1.0F - (float) Math.exp(-3.4F * safeExposure);
        return Mth.clamp(adapted * MAX_SHADE, 0.0F, MAX_SHADE);
    }

    static float maskedShadeAlpha(float exposure, float laserPreservation) {
        float preservation = Mth.clamp(laserPreservation, 0.0F, 1.0F);
        return shadeAlpha(exposure) * (1.0F - preservation);
    }
}
