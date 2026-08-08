package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure state-machine coverage for the normal DAY 1 through DAY 10 loop.
 *
 * <p>This deliberately does not construct a Minecraft server/player.  The
 * server transition code delegates its persistent state to these two SavedData
 * objects, so this test exercises quota, reset, and expansion invariants in a
 * fast deterministic test.
 */
class DayOneToTenSimulationTest {
    private static final int[] QUOTAS = {15, 30, 45, 60, 80, 80, 100, 120, 140, 170};

    @Test
    void normalProgressionCompletesEveryDayAndConfirmsOneExpansion() {
        FacilityDayData facility = new FacilityDayData();
        DepartmentProgressData departments = new DepartmentProgressData();

        facility.beginDay(1);
        assertDayReset(facility, 1);
        assertFalse(departments.prepareForDay(1), "DAY 1 has no expansion decision");
        assertEquals(0, departments.pendingDay());

        for (int day = 1; day <= 10; day++) {
            if (day > 1) {
                facility.beginDay(day);
                assertDayReset(facility, day);
                assertTrue(departments.prepareForDay(day), "DAY " + day + " should queue expansion");
                assertEquals(day, departments.pendingDay());

                EmployeeDepartment selected = departments.eligibleDepartments().stream()
                        .findFirst()
                        .orElse(null);
                assertNotNull(selected, "No legal expansion on DAY " + day);
                int currentDay = day;
                assertEquals(expectedExpansion(day), selected,
                        "Normal expansion order changed on DAY " + day);
                assertEquals(DepartmentProgressData.ExpandResult.SUCCESS,
                        departments.tryExpand(currentDay, selected));
                assertEquals(0, departments.pendingDay());

                // Duplicate confirmation must not consume a second expansion.
                assertEquals(DepartmentProgressData.ExpandResult.ALREADY_EXPANDED,
                        departments.tryExpand(currentDay, selected));
            }

            int quota = QUOTAS[day - 1];
            assertEquals(quota, facility.getEnergyQuota());
            facility.addEnergy(quota);
            assertEquals(quota, facility.getEnergy(), "Exact quota must complete DAY " + day);
            assertTrue(facility.getEnergy() >= facility.getEnergyQuota());

            // Work overproduction is legal, but energy is capped at the quota.
            facility.addEnergy(50);
            assertEquals(quota, facility.getEnergy(), "Over-quota energy must be clamped");
        }

        assertEquals(5, departments.level(EmployeeDepartment.CONTROL));
        assertEquals(5, departments.level(EmployeeDepartment.INFORMATION));
        assertEquals(0, departments.level(EmployeeDepartment.TRAINING));
    }

    @Test
    void companyWorkProgressAdvancesFromDayOneThroughDayTen() {
        com.wzz.lobotocraft.capability.CompanyDailyData company =
                new com.wzz.lobotocraft.capability.CompanyDailyData();

        for (int day = 1; day <= 10; day++) {
            assertEquals(day, company.getCurrentDay());
            assertEquals(day * 2, company.getRequiredWorkCount());
            for (int i = 0; i < company.getRequiredWorkCount(); i++) {
                company.addWorkCount();
            }
            assertTrue(company.hasCompletedTodayWork());
            assertEquals(day * 2, company.getTodayWorkCount());

            if (day < 10) {
                assertTrue(company.tryAdvanceToNextDay());
                assertEquals(day + 1, company.getCurrentDay());
                assertEquals(0, company.getTodayWorkCount());
                assertFalse(company.hasCompletedTodayWork());
            }
        }
        assertEquals(10, company.getCurrentDay());
    }

    @Test
    void schedulesArePreparedOncePerDayAndEarlyDaysHaveNoLockedTiers() {
        FacilityDayData facility = new FacilityDayData();
        for (int day = 1; day <= 10; day++) {
            facility.beginDay(day);
            assertFalse(facility.isSchedulePrepared());
            facility.prepareSchedule(0x1234ABCDL);
            assertTrue(facility.isSchedulePrepared());
            for (int stage = 0; stage < 10; stage++) {
                if (day < OrdealSchedulePlanner.DAWN_START_DAY) {
                    assertEquals(OrdealTier.NONE, facility.getScheduledOrdeal(stage));
                    assertEquals(0, facility.getScheduledOrdealColor(stage));
                }
            }
            assertNotNull(facility.getSchedule());
            assertEquals(10, facility.getSchedule().length);
        }
    }

    @Test
    void hostileEnergyInputsSaturateAndNeverWrap() {
        FacilityDayData facility = new FacilityDayData();
        facility.beginDay(3);

        facility.addEnergy(Integer.MAX_VALUE);
        assertEquals(facility.getEnergyQuota(), facility.getEnergy());

        facility.subtractEnergy(Integer.MAX_VALUE);
        assertEquals(0, facility.getEnergy());

        facility.addEnergy(-1);
        assertEquals(0, facility.getEnergy(), "negative command input must not add energy");
    }

    @Test
    void hostileCountersSaturateInsteadOfWrapping() {
        FacilityDayData facility = new FacilityDayData();
        facility.beginDay(1);
        CompoundTag tag = facility.save(new CompoundTag());
        tag.putInt("MeltdownGauge", Integer.MAX_VALUE);
        UUID target = UUID.randomUUID();
        net.minecraft.nbt.ListTag streaks = new net.minecraft.nbt.ListTag();
        CompoundTag streak = new CompoundTag();
        streak.putUUID("Target", target);
        streak.putInt("Count", Integer.MAX_VALUE);
        streaks.add(streak);
        tag.put("ConsecutiveWorks", streaks);

        FacilityDayData loaded = FacilityDayData.load(tag);
        assertTrue(loaded.advanceGauge());
        assertEquals(Integer.MAX_VALUE, loaded.getMeltdownGauge());
        loaded.incrementConsecutiveWorks(target);
        assertEquals(Integer.MAX_VALUE, loaded.getConsecutiveWorks(target));
    }

    @Test
    void savedDayStateReloadsWithoutRerollingOrDroppingProgress() {
        FacilityDayData facility = new FacilityDayData();
        facility.beginDay(3);
        facility.addEnergy(12);
        facility.advanceGauge();
        facility.prepareSchedule(0x1234ABCDL);
        int selectedColor = facility.getScheduledOrdealColor(2);

        FacilityDayData reloaded = FacilityDayData.load(facility.save(new net.minecraft.nbt.CompoundTag()));
        reloaded.prepareSchedule(Long.MIN_VALUE);

        assertEquals(3, reloaded.getDay());
        assertEquals(12, reloaded.getEnergy());
        assertEquals(1, reloaded.getMeltdownGauge());
        assertEquals(selectedColor, reloaded.getScheduledOrdealColor(2));
    }

    @Test
    void staleExpansionConfirmationCannotAdvanceAnotherDay() {
        DepartmentProgressData departments = new DepartmentProgressData();
        assertTrue(departments.prepareForDay(2));
        assertEquals(DepartmentProgressData.ExpandResult.PENDING_DAY_MISMATCH,
                departments.tryExpand(3, EmployeeDepartment.CONTROL));
        assertEquals(2, departments.pendingDay());
        assertEquals(DepartmentProgressData.ExpandResult.SUCCESS,
                departments.tryExpand(2, EmployeeDepartment.CONTROL));
        assertEquals(DepartmentProgressData.ExpandResult.ALREADY_EXPANDED,
                departments.tryExpand(2, EmployeeDepartment.CONTROL));
    }

    @Test
    void expansionReceiptSurvivesReloadBetweenDays() {
        DepartmentProgressData data = new DepartmentProgressData();
        assertTrue(data.prepareForDay(2));
        assertEquals(DepartmentProgressData.ExpandResult.SUCCESS,
                data.tryExpand(2, EmployeeDepartment.CONTROL));

        DepartmentProgressData loaded = DepartmentProgressData.load(data.save(new CompoundTag()));
        assertEquals(2, loaded.level(EmployeeDepartment.CONTROL));
        assertEquals(2, loaded.lastExpandedDay());
        assertEquals(0, loaded.pendingDay());
        assertTrue(loaded.prepareForDay(3));
        assertEquals(DepartmentProgressData.ExpandResult.ALREADY_EXPANDED,
                loaded.tryExpand(2, EmployeeDepartment.CONTROL));
        assertEquals(3, loaded.pendingDay());
    }

    private static void assertDayReset(FacilityDayData facility, int day) {
        assertEquals(day, facility.getDay());
        assertEquals(0, facility.getEnergy());
        assertEquals(1, facility.getMeltdownLevel());
        assertEquals(0, facility.getMeltdownGauge());
        assertEquals(OrdealTier.NONE, facility.getNextOrdeal());
        assertEquals(0, facility.getNoonStage());
        assertFalse(facility.isDawnTriggered());
        assertFalse(facility.isNoonTriggered());
        assertTrue(facility.getActiveMeltdowns().isEmpty());
    }

    private static EmployeeDepartment expectedExpansion(int day) {
        return switch (day) {
            case 2 -> EmployeeDepartment.CONTROL;
            case 3 -> EmployeeDepartment.CONTROL;
            case 4 -> EmployeeDepartment.CONTROL;
            case 5 -> EmployeeDepartment.CONTROL;
            case 6, 7, 8, 9, 10 -> EmployeeDepartment.INFORMATION;
            default -> throw new IllegalArgumentException("Unsupported day " + day);
        };
    }
}
