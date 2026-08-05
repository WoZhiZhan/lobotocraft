package com.wzz.lobotocraft.entity.abnormality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IronMaidenGenerationPolicyTest {
    @Test
    void firstPulseUsesTheOriginalDamageEnergyAndFrequencyOrder() {
        IronMaidenGenerationPolicy.Pulse pulse =
                IronMaidenGenerationPolicy.nextPulse(0);

        assertEquals(0.0D, pulse.damage(), 1.0E-12D);
        assertEquals(1.0D / 110.0D, pulse.generatedEnergy(), 1.0E-12D);
        assertEquals(1, pulse.completedPulses());
        assertEquals(38, pulse.nextIntervalTicks());
    }

    @Test
    void tenPulsesGenerateTheOriginalSumOfSquaresEnergy() {
        int completed = 0;
        double generated = 0.0D;
        IronMaidenGenerationPolicy.Pulse pulse = null;
        for (int i = 0; i < 10; i++) {
            pulse = IronMaidenGenerationPolicy.nextPulse(completed);
            completed = pulse.completedPulses();
            generated += pulse.generatedEnergy();
        }

        assertEquals(3.5D, generated, 1.0E-9D);
        assertEquals(81.0D / 30.0D, pulse.damage(), 1.0E-12D);
    }

    @Test
    void pulseIntervalFallsByPointOneSecondsAndStopsAtPointOne() {
        assertEquals(40, IronMaidenGenerationPolicy.intervalTicks(0));
        assertEquals(20, IronMaidenGenerationPolicy.intervalTicks(10));
        assertEquals(2, IronMaidenGenerationPolicy.intervalTicks(19));
        assertEquals(2, IronMaidenGenerationPolicy.intervalTicks(100));
    }
}
