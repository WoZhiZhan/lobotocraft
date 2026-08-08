package com.wzz.lobotocraft.event.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentalValueEventHandlerTest {
    @Test
    void whiteDamageOnlyRecoversPanicFromAStablePlayerAttacker() {
        assertTrue(MentalValueEventHandler.shouldRecoverPanicFromWhite(true, true, false));
        assertFalse(MentalValueEventHandler.shouldRecoverPanicFromWhite(false, true, false));
        assertFalse(MentalValueEventHandler.shouldRecoverPanicFromWhite(true, false, false));
        assertFalse(MentalValueEventHandler.shouldRecoverPanicFromWhite(true, true, true));
    }
}
