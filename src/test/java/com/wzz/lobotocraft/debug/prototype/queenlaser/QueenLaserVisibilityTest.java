package com.wzz.lobotocraft.debug.prototype.queenlaser;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueenLaserVisibilityTest {
    private static final Vec3 CAMERA = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 START = new Vec3(-8.0D, 0.0D, 12.0D);
    private static final Vec3 END = new Vec3(8.0D, 0.0D, 12.0D);

    @Test
    void fullyOccludedBeamDoesNotChangeExposure() {
        assertEquals(0.0F, QueenLaserVisibility.measure(
                CAMERA, LOOK, START, END, 2.0F, 16, index -> false));
    }

    @Test
    void exposureTracksHowMuchOfTheBeamIsVisible() {
        float half = QueenLaserVisibility.measure(
                CAMERA, LOOK, START, END, 2.0F, 16, index -> index < 8);
        float full = QueenLaserVisibility.measure(
                CAMERA, LOOK, START, END, 2.0F, 16, index -> true);

        assertTrue(half > 0.0F);
        assertTrue(full > half);
    }

    @Test
    void beamBehindCameraDoesNotChangeExposure() {
        assertEquals(0.0F, QueenLaserVisibility.measure(
                CAMERA, LOOK, new Vec3(-8.0D, 0.0D, -12.0D),
                new Vec3(8.0D, 0.0D, -12.0D), 2.0F, 16, index -> true));
    }
}
