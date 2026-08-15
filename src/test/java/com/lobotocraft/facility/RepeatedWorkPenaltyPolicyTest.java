package com.lobotocraft.facility;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatedWorkPenaltyPolicyTest {
    @Test
    void wawCapsAtTwentyFourPercent() {
        assertEquals(0, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.WAW, 0));
        assertEquals(20, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.WAW, 5));
        assertEquals(24, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.WAW, 99));
    }

    @Test
    void alephCapsAtThirtyTwoPercent() {
        assertEquals(0, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.ALEPH, 0));
        assertEquals(30, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.ALEPH, 5));
        assertEquals(32, FacilityManager.repeatedWorkPenaltyPercent(RiskLevel.ALEPH, 6));
    }
}
