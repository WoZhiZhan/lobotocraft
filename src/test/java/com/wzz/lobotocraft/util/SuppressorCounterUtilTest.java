package com.wzz.lobotocraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SuppressorCounterUtilTest {
    @Test
    void capacityIncreasesByFortyUntilDaySix() {
        assertEquals(120, SuppressorCounterUtil.capacityForDay(1));
        assertEquals(160, SuppressorCounterUtil.capacityForDay(2));
        assertEquals(200, SuppressorCounterUtil.capacityForDay(3));
        assertEquals(240, SuppressorCounterUtil.capacityForDay(4));
        assertEquals(280, SuppressorCounterUtil.capacityForDay(5));
        assertEquals(320, SuppressorCounterUtil.capacityForDay(6));
    }

    @Test
    void capacityHandlesInvalidAndExtremeDaysWithoutOverflow() {
        assertEquals(120, SuppressorCounterUtil.capacityForDay(0));
        assertEquals(120, SuppressorCounterUtil.capacityForDay(Integer.MIN_VALUE));
        assertEquals(320, SuppressorCounterUtil.capacityForDay(50));
        assertEquals(320, SuppressorCounterUtil.capacityForDay(Integer.MAX_VALUE));
    }
}
