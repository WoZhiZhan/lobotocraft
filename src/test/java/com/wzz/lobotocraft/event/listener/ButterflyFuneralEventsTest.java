package com.wzz.lobotocraft.event.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButterflyFuneralEventsTest {
    @Test
    void aiRescueCannotAttachPaleDamageToWhitePanicRecovery() {
        assertFalse(ButterflyFuneralEvents.mayAddPaleBonus(true, true, false));
        assertTrue(ButterflyFuneralEvents.mayAddPaleBonus(true, true, true));
        assertTrue(ButterflyFuneralEvents.mayAddPaleBonus(true, false, false));
        assertTrue(ButterflyFuneralEvents.mayAddPaleBonus(false, true, false));
    }
}
