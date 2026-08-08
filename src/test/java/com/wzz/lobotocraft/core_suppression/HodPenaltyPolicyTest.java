package com.wzz.lobotocraft.core_suppression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HodPenaltyPolicyTest {
    @Test
    void mapsTheThreeSuppressionPhasesToTenTwentyAndThirty() {
        assertEquals(0, HodPenaltyPolicy.visualStage(1));
        assertEquals(10, HodPenaltyPolicy.penalty(1));

        assertEquals(1, HodPenaltyPolicy.visualStage(2));
        assertEquals(20, HodPenaltyPolicy.penalty(2));
        assertEquals(20, HodPenaltyPolicy.penalty(3));

        assertEquals(2, HodPenaltyPolicy.visualStage(4));
        assertEquals(30, HodPenaltyPolicy.penalty(4));
        assertEquals(30, HodPenaltyPolicy.penalty(10));
    }
}
