package com.lobotocraft.facility.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
