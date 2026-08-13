package com.wzz.lobotocraft.world.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrdealDataConcurrencyTest {
    @Test
    void dawnAndMiddayCompleteIndependently() {
        OrdealData data = new OrdealData();

        data.startGreenDawn(1);
        data.startCrimsonMidday(1);
        assertTrue(data.hasActiveDawn());
        assertTrue(data.hasActiveMidday());

        data.decrementGreenDawnRemaining();
        data.finishGreenDawn();
        assertFalse(data.hasActiveDawn());
        assertTrue(data.hasActiveMidday());

        data.decrementCrimsonMiddayRemaining();
        data.finishCrimsonMidday();
        assertFalse(data.hasActiveMidday());
        assertFalse(data.hasActiveOrdeal());
    }

    @Test
    void exhibitionAbortClearsEveryTier() {
        OrdealData data = new OrdealData();
        data.startAmberDawn(2);
        data.startVioletMidday(3);
        data.startLateOrdeal(1, 2, java.util.List.of());

        assertTrue(data.hasActiveOrdeal());
        data.abortAllActiveOrdeals();
        assertFalse(data.hasActiveOrdeal());
    }
}
