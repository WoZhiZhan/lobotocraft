package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class OrdealSchedulePlannerTest {
    @Test
    void schedulesOneOriginalStageForEveryUnlockedTier() {
        OrdealSchedulePlanner.Plan plan = OrdealSchedulePlanner.create(26, 0x12345678L);

        assertEquals(OrdealTier.DAWN, plan.tierAt(2));
        assertEquals(OrdealTier.DAWN, plan.tierAt(3));
        assertTrue(plan.colorAt(2) != plan.colorAt(3));
        assertEquals(1, count(plan, OrdealTier.NOON, 4, 5));
        assertEquals(1, count(plan, OrdealTier.DUSK, 6, 7));
        assertEquals(OrdealTier.MIDNIGHT, plan.tierAt(8));
        int duskStage = plan.tierAt(6) == OrdealTier.DUSK ? 6 : 7;
        assertTrue(OrdealColorPool.supports(OrdealTier.DUSK, plan.colorAt(duskStage)));
        assertTrue(OrdealColorPool.supports(OrdealTier.MIDNIGHT, plan.colorAt(8)));
    }

    @Test
    void followsConfiguredTierUnlockDays() {
        assertTierCounts(2, 0, 0, 0, 0);
        assertTierCounts(3, 2, 0, 0, 0);
        assertTierCounts(10, 2, 0, 0, 0);
        assertTierCounts(11, 2, 1, 0, 0);
        assertTierCounts(20, 2, 1, 0, 0);
        assertTierCounts(21, 2, 1, 1, 0);
        assertTierCounts(25, 2, 1, 1, 0);
        assertTierCounts(26, 2, 1, 1, 1);
    }

    @Test
    void sameWorldAndDayNeverRerolls() {
        OrdealSchedulePlanner.Plan first = OrdealSchedulePlanner.create(23, 99182731L);
        OrdealSchedulePlanner.Plan second = OrdealSchedulePlanner.create(23, 99182731L);

        assertArrayEquals(first.tiers(), second.tiers());
        assertArrayEquals(first.colors(), second.colors());
    }

    @Test
    void savedDailyScheduleSurvivesReloadWithoutUsingNewRandomness() {
        FacilityDayData data = new FacilityDayData();
        data.beginDay(26);
        data.prepareSchedule(123L);
        int dawnTwo = data.getScheduledOrdealColor(2);
        int dawnThree = data.getScheduledOrdealColor(3);
        int noonFour = data.getScheduledOrdealColor(4);
        int noonFive = data.getScheduledOrdealColor(5);

        FacilityDayData loaded = FacilityDayData.load(data.save(new CompoundTag()));
        loaded.prepareSchedule(999999L);

        assertEquals(dawnTwo, loaded.getScheduledOrdealColor(2));
        assertEquals(dawnThree, loaded.getScheduledOrdealColor(3));
        assertTrue(dawnTwo != dawnThree);
        assertEquals(noonFour, loaded.getScheduledOrdealColor(4));
        assertEquals(noonFive, loaded.getScheduledOrdealColor(5));
        assertEquals(OrdealTier.MIDNIGHT, loaded.getScheduledOrdeal(8));
    }

    @Test
    void loadingAnOldEarlyDaySaveRemovesPreviouslyScheduledLockedTiers() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Day", 2);
        legacy.putBoolean("SchedulePrepared", true);
        legacy.putInt("ScheduleTier2", OrdealTier.DAWN.ordinal());
        legacy.putInt("ScheduleColor2", 2);
        legacy.putInt("ScheduleTier5", OrdealTier.NOON.ordinal());
        legacy.putInt("ScheduleColor5", 3);
        legacy.putInt("ScheduleTier8", OrdealTier.DUSK.ordinal());
        legacy.putInt("ScheduleColor8", 1);
        legacy.putInt("ScheduleTier9", OrdealTier.MIDNIGHT.ordinal());
        legacy.putInt("ScheduleColor9", 2);
        legacy.putInt("NextOrdeal", OrdealTier.MIDNIGHT.ordinal());
        legacy.putInt("NextOrdealColor", 2);

        FacilityDayData loaded = FacilityDayData.load(legacy);

        assertEquals(OrdealTier.NONE, loaded.getScheduledOrdeal(2));
        assertEquals(OrdealTier.NONE, loaded.getScheduledOrdeal(5));
        assertEquals(OrdealTier.NONE, loaded.getScheduledOrdeal(8));
        assertEquals(OrdealTier.NONE, loaded.getScheduledOrdeal(9));
        assertEquals(0, loaded.getScheduledOrdealColor(9));
        assertEquals(OrdealTier.NONE, loaded.getNextOrdeal());
        assertEquals(0, loaded.getNextOrdealColor());
    }

    private static int count(OrdealSchedulePlanner.Plan plan, OrdealTier tier, int... stages) {
        int count = 0;
        for (int stage : stages) if (plan.tierAt(stage) == tier) count++;
        return count;
    }

    private static void assertTierCounts(int day, int dawn, int noon, int dusk, int midnight) {
        OrdealSchedulePlanner.Plan plan = OrdealSchedulePlanner.create(day, 0x12345678L);
        assertEquals(dawn, count(plan, OrdealTier.DAWN, 2, 3));
        assertEquals(noon, count(plan, OrdealTier.NOON, 4, 5));
        assertEquals(dusk, count(plan, OrdealTier.DUSK, 6, 7));
        assertEquals(midnight, count(plan, OrdealTier.MIDNIGHT, 8));
    }
}
