package com.wzz.lobotocraft.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecruitmentServiceTest {
    @Test
    void recruitmentLevelsUseOriginalLowerBoundValues() {
        assertEquals(20, RecruitmentService.statValue(1));
        assertEquals(30, RecruitmentService.statValue(2));
        assertEquals(45, RecruitmentService.statValue(3));
        assertEquals(65, RecruitmentService.statValue(4));
        assertEquals(85, RecruitmentService.statValue(5));
    }
}
