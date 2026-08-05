package com.wzz.lobotocraft.capability;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeStatsTest {
    @Test
    void clampsAllMutationAndLoadPathsToTwentyThroughOneHundredTen() {
        EmployeeStats stats = new EmployeeStats();
        stats.setFortitude(999);
        stats.setPrudence(-999);
        assertEquals(110, stats.getFortitude());
        assertEquals(20, stats.getPrudence());
        assertEquals(0, stats.addFortitude(50));

        CompoundTag editedSave = new CompoundTag();
        editedSave.putInt("Fortitude", 5000);
        editedSave.putInt("Prudence", -5000);
        editedSave.putInt("Temperance", 100);
        editedSave.putInt("Justice", 110);
        stats.loadFromNBT(editedSave);

        assertEquals(110, stats.getFortitude());
        assertEquals(20, stats.getPrudence());
        assertEquals(100, stats.getTemperance());
        assertEquals(110, stats.getJustice());
        assertEquals(5, stats.getTemperanceLevel());
        assertEquals(5, stats.getJusticeLevel());
    }

    @Test
    void hodPenaltyCanReduceEffectiveStatsBelowTwentyButNeverBelowOne() {
        EmployeeStats stats = new EmployeeStats();
        stats.setFortitude(20);
        stats.setPrudence(29);
        stats.setTemperance(45);
        stats.setJustice(85);

        stats.setHodPenalty(30);

        assertEquals(1, stats.getFortitude());
        assertEquals(1, stats.getPrudence());
        assertEquals(15, stats.getTemperance());
        assertEquals(55, stats.getJustice());
        assertEquals(1, stats.getFortitudeLevel());
        assertEquals(1, stats.getPrudenceLevel());
        assertEquals(1, stats.getTemperanceLevel());
        assertEquals(3, stats.getJusticeLevel());
        assertEquals(2, stats.getEmployeeLevel());

        assertEquals(20, stats.getBaseFortitude());
        assertEquals(29, stats.getBasePrudence());
        assertEquals(45, stats.getBaseTemperance());
        assertEquals(85, stats.getBaseJustice());
    }

    @Test
    void hodPenaltyIsAnIdempotentNonPersistentOverlay() {
        EmployeeStats stats = new EmployeeStats();
        stats.setFortitude(50);
        stats.setHodPenalty(20);
        stats.setHodPenalty(20);
        assertEquals(30, stats.getFortitude());
        assertEquals(2, stats.getFortitudeLevel());

        stats.addFortitude(10);
        assertEquals(40, stats.getFortitude());
        assertEquals(60, stats.getBaseFortitude());

        CompoundTag saved = new CompoundTag();
        stats.saveToNBT(saved);
        EmployeeStats loaded = new EmployeeStats();
        loaded.loadFromNBT(saved);
        assertEquals(60, loaded.getFortitude());
        assertEquals(0, loaded.getHodPenalty());

        stats.clearHodPenalty();
        assertEquals(60, stats.getFortitude());
        stats.clearHodPenalty();
        assertEquals(60, stats.getFortitude());
    }
}
