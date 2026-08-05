package com.wzz.lobotocraft.entity.abnormality;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EndBirdMeltdownServiceTest {
    @Test
    void everyEggCycleStaysWithinOriginalSixtyToOneHundredTwentySeconds() {
        RandomSource random = RandomSource.create(0xB055B1D5L);
        for (int i = 0; i < 10_000; i++) {
            int delay = EndBirdMeltdownService.nextDelayTicks(random);
            assertTrue(delay >= EndBirdMeltdownService.MIN_DELAY_TICKS);
            assertTrue(delay <= EndBirdMeltdownService.MAX_DELAY_TICKS);
        }
    }
}
