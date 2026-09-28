package com.wzz.lobotocraft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenDistortionStateTest {
    @Test
    void worldUnloadMustDiscardLongRunningDistortion() {
        ScreenDistortionState.activate(0.8f, 1200);
        ScreenDistortionState.tick();
        assertTrue(ScreenDistortionState.isActive());

        ScreenDistortionState.clear();
        assertFalse(ScreenDistortionState.isActive());
    }
}
