package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrdealRewardPolicyTest {
    @Test
    void usesAdjustedDawnAndOriginalLaterTierPercentages() {
        assertEquals(0, OrdealRewardPolicy.rewardPercent(OrdealTier.NONE));
        assertEquals(10, OrdealRewardPolicy.rewardPercent(OrdealTier.DAWN));
        assertEquals(15, OrdealRewardPolicy.rewardPercent(OrdealTier.NOON));
        assertEquals(20, OrdealRewardPolicy.rewardPercent(OrdealTier.DUSK));
        assertEquals(25, OrdealRewardPolicy.rewardPercent(OrdealTier.MIDNIGHT));
    }

    @Test
    void roundsPercentageRewardsToNearestWholeUnit() {
        assertEquals(2, OrdealRewardPolicy.rewardForMaximum(15, OrdealTier.DAWN));
        assertEquals(12, OrdealRewardPolicy.rewardForMaximum(120, OrdealTier.DAWN));
        assertEquals(32, OrdealRewardPolicy.rewardForMaximum(320, OrdealTier.DAWN));
        assertEquals(7, OrdealRewardPolicy.rewardForMaximum(45, OrdealTier.NOON));
        assertEquals(64, OrdealRewardPolicy.rewardForMaximum(320, OrdealTier.DUSK));
        assertEquals(80, OrdealRewardPolicy.rewardForMaximum(320, OrdealTier.MIDNIGHT));
    }

    @Test
    void rejectsInvalidMaximumsAndAvoidsOverflow() {
        assertEquals(0, OrdealRewardPolicy.rewardForMaximum(0, OrdealTier.DAWN));
        assertEquals(0, OrdealRewardPolicy.rewardForMaximum(-1, OrdealTier.DAWN));
        assertEquals(536_870_912,
                OrdealRewardPolicy.rewardForMaximum(Integer.MAX_VALUE, OrdealTier.MIDNIGHT));
    }
}
