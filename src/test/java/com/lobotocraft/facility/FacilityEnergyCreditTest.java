package com.lobotocraft.facility;

import com.lobotocraft.facility.data.FacilityDayData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacilityEnergyCreditTest {
    @Test
    void toolOutputCreditsTheSharedPoolWithoutAnOperatorOwner() {
        FacilityDayData data = new FacilityDayData();

        assertEquals(4.0D, FacilityManager.creditEnergy(data, 4.0D), 1.0E-12D);
        assertEquals(4, data.getEnergy());
    }

    @Test
    void onlyEnergyAcceptedBeforeTheQuotaIsReported() {
        FacilityDayData data = new FacilityDayData();
        data.addEnergy(14);

        assertEquals(1.0D, FacilityManager.creditEnergy(data, 8.0D), 1.0E-12D);
        assertEquals(15, data.getEnergy());
        assertEquals(0.0D, FacilityManager.creditEnergy(data, 8.0D), 1.0E-12D);
    }

    @Test
    void nonPositiveOutputCannotReduceDailyEnergy() {
        FacilityDayData data = new FacilityDayData();
        data.addEnergy(5);

        assertEquals(0.0D, FacilityManager.creditEnergy(data, 0.0D), 1.0E-12D);
        assertEquals(0.0D, FacilityManager.creditEnergy(data, -3.0D), 1.0E-12D);
        assertEquals(5, data.getEnergy());
    }

    @Test
    void fractionalOutputAccumulatesGloballyAcrossDifferentMachines() {
        FacilityDayData data = new FacilityDayData();

        assertEquals(0.6D, FacilityManager.creditEnergy(data, 0.6D), 1.0E-12D);
        assertEquals(0, data.getEnergy());
        assertEquals(0.6D, data.getExactEnergy(), 1.0E-12D);
        assertEquals(0.6D, FacilityManager.creditEnergy(data, 0.6D), 1.0E-12D);
        assertEquals(1, data.getEnergy());
        assertEquals(1.2D, data.getExactEnergy(), 1.0E-12D);

        FacilityDayData reloaded = FacilityDayData.load(
                data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(1.2D, reloaded.getExactEnergy(), 1.0E-12D);
    }
}
