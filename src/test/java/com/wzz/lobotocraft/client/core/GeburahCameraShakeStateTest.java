package com.wzz.lobotocraft.client.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahCameraShakeStateTest {
    @AfterEach
    void clear() {
        GeburahCameraShakeState.clear();
    }

    @Test
    void shakeIsBoundedAndDecaysToZero() {
        GeburahCameraShakeState.trigger(6, 0.8F, 17);

        GeburahCameraShakeState.Sample first = GeburahCameraShakeState.sample(0.0F);
        assertTrue(Math.abs(first.yaw()) <= 0.8F);
        assertTrue(Math.abs(first.pitch()) <= 0.8F);
        assertTrue(Math.abs(first.roll()) <= 0.8F);

        for (int i = 0; i < 6; i++) GeburahCameraShakeState.tick();

        assertFalse(GeburahCameraShakeState.isActive());
        assertEquals(GeburahCameraShakeState.Sample.ZERO,
                GeburahCameraShakeState.sample(0.0F));
    }

    @Test
    void packetValuesAreClampedToComfortLimits() {
        GeburahCameraShakeState.trigger(200, 20.0F, 3);

        assertEquals(GeburahCameraShakeState.MAX_DURATION_TICKS,
                GeburahCameraShakeState.remainingTicks());
        assertEquals(GeburahCameraShakeState.MAX_AMPLITUDE,
                GeburahCameraShakeState.amplitude());
    }
}
