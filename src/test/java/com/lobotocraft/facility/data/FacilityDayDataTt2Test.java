package com.lobotocraft.facility.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

class FacilityDayDataTt2Test {
    @Test
    void globalTt2SwitchPersistsAcrossSaveAndReload() {
        FacilityDayData data = new FacilityDayData();
        assertFalse(data.isTt2ProtocolEnabled());

        assertTrue(data.toggleTt2Protocol());
        assertTrue(data.isTt2ProtocolEnabled());

        FacilityDayData loaded = FacilityDayData.load(data.save(new CompoundTag()));
        assertTrue(loaded.isTt2ProtocolEnabled());
        assertFalse(loaded.toggleTt2Protocol());
    }

    @Test
    void exhibitionRecoveryClearsAllMeltdowns() {
        FacilityDayData data = new FacilityDayData();
        data.addMeltdown(java.util.UUID.randomUUID(), 100L, 2, "test-a");
        data.addMeltdown(java.util.UUID.randomUUID(), 120L, 2, "test-b");

        assertEquals(2, data.clearActiveMeltdowns());
        assertTrue(data.getActiveMeltdowns().isEmpty());
        assertEquals(0, data.clearActiveMeltdowns());
    }

    @Test
    void advancingIntoTheNextQliphothStageClearsRepeatedWorkCounters() {
        FacilityDayData data = new FacilityDayData();
        data.beginDay(1);
        UUID target = UUID.randomUUID();
        data.incrementConsecutiveWorks(target, 8);
        assertEquals(1, data.getConsecutiveWorks(target));

        for (int i = 1; i < data.getMeltdownThreshold(); i++) {
            assertFalse(data.advanceGauge());
        }
        assertTrue(data.advanceGauge());
        assertEquals(0, data.getConsecutiveWorks(target));
    }
}
