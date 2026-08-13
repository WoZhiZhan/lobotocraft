package com.wzz.lobotocraft.ego;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiEgoRepositoryDataTest {
    @Test
    void researchToMaximumIsExactAndIdempotent() {
        AiEgoRepositoryData data = new AiEgoRepositoryData();
        List<EgoItemSpec> rewards = List.of(new EgoItemSpec("minecraft:iron_sword", null));

        assertEquals(5, data.researchSpecsToMaximum("O-00-00", "weapon", rewards, 5));
        assertEquals(5, data.total("O-00-00", "weapon"));
        assertEquals(0, data.researchSpecsToMaximum("O-00-00", "weapon", rewards, 5));
        assertEquals(5, data.stock("O-00-00", "weapon").maximum());
    }

    @Test
    void uniqueSuppressionRewardNeverExceedsOne() {
        AiEgoRepositoryData data = new AiEgoRepositoryData();
        List<EgoItemSpec> rewards = List.of(new EgoItemSpec("minecraft:iron_sword", null));

        assertEquals(1, data.researchSpecsToMaximum("S-03-07", "weapon", rewards, 1));
        assertEquals(0, data.researchSpecsToMaximum("S-03-07", "weapon", rewards, 1));
        assertEquals(1, data.total("S-03-07", "weapon"));
    }
}
