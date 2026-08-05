package com.wzz.lobotocraft.event.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionShaderHandlerTest {

    @Test
    void reloadDelayUsesBoundedExponentialBackoff() {
        assertEquals(20, ResolutionShaderHandler.reloadDelayTicks(1));
        assertEquals(40, ResolutionShaderHandler.reloadDelayTicks(2));
        assertEquals(80, ResolutionShaderHandler.reloadDelayTicks(3));
        assertEquals(160, ResolutionShaderHandler.reloadDelayTicks(4));
        assertEquals(320, ResolutionShaderHandler.reloadDelayTicks(5));
        assertEquals(600, ResolutionShaderHandler.reloadDelayTicks(6));
        assertEquals(600, ResolutionShaderHandler.reloadDelayTicks(30));
    }

    @Test
    void invalidFailureCountStillUsesMinimumDelay() {
        assertEquals(20, ResolutionShaderHandler.reloadDelayTicks(0));
        assertEquals(20, ResolutionShaderHandler.reloadDelayTicks(-10));
    }
}
