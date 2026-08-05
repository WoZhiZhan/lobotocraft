package com.wzz.lobotocraft.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanyDailyDataTest {
    @Test
    void successfulAdvanceReportsSuccessAndMutatesDay() {
        CompanyDailyData data = new CompanyDailyData();
        data.setTodayWorkCount(12);
        assertTrue(data.tryAdvanceToNextDay());
        assertEquals(2, data.getCurrentDay());
        assertEquals(0, data.getTodayWorkCount());
    }
}
