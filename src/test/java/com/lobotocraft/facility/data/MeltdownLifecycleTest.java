package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import com.lobotocraft.facility.network.FacilityStatePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeltdownLifecycleTest {
    @Test
    void normalClockAdvancesFromPersistedRemainingTime() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        assertTrue(MeltdownLifecycle.advance(data, 110L).isEmpty());

        FacilityDayData.ActiveMeltdown instance = data.getActiveMeltdowns().get(target);
        assertEquals(1490L, instance.projectedRemainingTicks(110L));
        assertEquals(1600L, instance.deadline());
    }

    @Test
    void defaultDurationIsSeventyFiveSeconds() {
        assertEquals(20 * 75, MeltdownLifecycle.DEFAULT_DURATION_TICKS);
    }

    @Test
    void pausedClockDoesNotConsumeTimeAndResumesFromPauseTick() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        assertTrue(MeltdownLifecycle.setTimeRate(data, target, 110L, 0));
        long remainingAtPause = data.getActiveMeltdowns().get(target).remainingTimeUnits();
        MeltdownLifecycle.advance(data, 210L);
        assertEquals(remainingAtPause,
            data.getActiveMeltdowns().get(target).remainingTimeUnits());
        assertEquals(Long.MAX_VALUE,
            data.getActiveMeltdowns().get(target).projectedRemainingTicks(210L));

        assertTrue(MeltdownLifecycle.setTimeRate(data, target, 210L,
            MeltdownLifecycle.NORMAL_TIME_RATE_PERMILLE));
        MeltdownLifecycle.advance(data, 220L);
        assertEquals(remainingAtPause - 10L * FacilityDayData.ActiveMeltdown.TIME_UNITS_PER_TICK,
            data.getActiveMeltdowns().get(target).remainingTimeUnits());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 2000, 0})
    void facilityPauseRebasesClockAcrossReloadWithoutConsumingBudgetOrReplacingRate(int rate) {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        assertTrue(MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG));
        assertTrue(MeltdownLifecycle.advance(data, 110L).isEmpty());
        MeltdownLifecycle.setTimeRate(data, target, 110L, rate);
        long remainingAtPause = data.getActiveMeltdowns().get(target).remainingTimeUnits();
        data.setPaused(true);
        var snapshotAtPause = FacilityStatePacket.snapshotMeltdowns(data, 110L);

        for (long now : new long[]{210L, 410L}) {
            assertTrue(MeltdownLifecycle.advance(data, now).isEmpty());
            FacilityDayData.ActiveMeltdown paused = data.getActiveMeltdowns().get(target);
            var snapshot = FacilityStatePacket.snapshotMeltdowns(data, now);
            assertAll(
                () -> assertEquals(remainingAtPause, paused.remainingTimeUnits()),
                () -> assertEquals(now, paused.lastProcessedTick()),
                () -> assertEquals(rate, paused.timeRatePermille()),
                () -> assertEquals(snapshotAtPause, snapshot));

            data = FacilityDayData.load(data.save(new CompoundTag()));
            assertTrue(data.isPaused());
            assertEquals(paused, data.getActiveMeltdowns().get(target));
            assertEquals(snapshotAtPause, FacilityStatePacket.snapshotMeltdowns(data, now));
        }

        data.setPaused(false);
        assertTrue(MeltdownLifecycle.advance(data, 411L).isEmpty());
        FacilityDayData.ActiveMeltdown resumed = data.getActiveMeltdowns().get(target);
        assertAll(
            () -> assertEquals(remainingAtPause - rate, resumed.remainingTimeUnits(),
                "Resuming must consume only one tick at the preserved rate"),
            () -> assertEquals(411L, resumed.lastProcessedTick()),
            () -> assertEquals(rate, resumed.timeRatePermille()));
    }

    @Test
    void doubleRateConsumesTwoTicksOfBudgetPerServerTick() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        MeltdownLifecycle.setTimeRate(data, target, 100L, 2000);
        MeltdownLifecycle.advance(data, 110L);

        FacilityDayData.ActiveMeltdown instance = data.getActiveMeltdowns().get(target);
        assertEquals(1480L * FacilityDayData.ActiveMeltdown.TIME_UNITS_PER_TICK,
            instance.remainingTimeUnits());
        assertEquals(740L, instance.projectedRemainingTicks(110L));
    }

    @Test
    void legacyDeadlineMigratesOnFirstTickAndPersistsDynamicFields() {
        UUID target = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        ListTag meltdowns = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Target", target);
        entry.putLong("Deadline", 1200L);
        entry.putInt("Level", 2);
        meltdowns.add(entry);
        root.put("ActiveMeltdowns", meltdowns);
        FacilityDayData data = FacilityDayData.load(root);

        assertFalse(data.getActiveMeltdowns().get(target).usesDynamicClock());
        assertTrue(MeltdownLifecycle.advance(data, 1000L).isEmpty());
        assertEquals(200L, data.getActiveMeltdowns().get(target).projectedRemainingTicks(1000L));

        FacilityDayData reloaded = FacilityDayData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.getActiveMeltdowns().get(target).usesDynamicClock());
        assertEquals(200L,
            reloaded.getActiveMeltdowns().get(target).projectedRemainingTicks(1000L));
    }

    @Test
    void savedPausedLegacyDeadlineMigratesOnceThenFreezesUntilResume() {
        UUID target = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        root.putBoolean("Paused", true);
        ListTag meltdowns = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Target", target);
        entry.putLong("Deadline", 1200L);
        entry.putInt("Level", 2);
        meltdowns.add(entry);
        root.put("ActiveMeltdowns", meltdowns);
        FacilityDayData data = FacilityDayData.load(root);

        assertTrue(MeltdownLifecycle.advance(data, 1000L).isEmpty());
        FacilityDayData.ActiveMeltdown migrated = data.getActiveMeltdowns().get(target);
        assertTrue(migrated.usesDynamicClock());
        assertEquals(200_000L, migrated.remainingTimeUnits());
        data = FacilityDayData.load(data.save(new CompoundTag()));
        assertTrue(data.isPaused());
        assertEquals(migrated, data.getActiveMeltdowns().get(target));

        assertTrue(MeltdownLifecycle.advance(data, 2000L).isEmpty());
        assertEquals(200_000L, data.getActiveMeltdowns().get(target).remainingTimeUnits());
        assertEquals(2000L, data.getActiveMeltdowns().get(target).lastProcessedTick());
        assertEquals(2200L, data.getActiveMeltdowns().get(target).deadline());
        data.setPaused(false);
        assertTrue(MeltdownLifecycle.advance(data, 2001L).isEmpty());
        assertEquals(199_000L, data.getActiveMeltdowns().get(target).remainingTimeUnits());
    }

    @Test
    void pausedFacilityDefersExpiryDispatchEvenForAlreadyExhaustedBudget() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        MeltdownLifecycle.start(data, target, 100L, 2, "O-03-89",
            MeltdownCause.APOCALYPSE_BIRD_EGG);
        long expiredAt = 100L + MeltdownLifecycle.DEFAULT_DURATION_TICKS;
        assertEquals(List.of(target), MeltdownLifecycle.advance(data, expiredAt));
        data.setPaused(true);

        assertTrue(MeltdownLifecycle.advance(data, expiredAt + 100L).isEmpty());
        FacilityDayData.ActiveMeltdown paused = data.getActiveMeltdowns().get(target);
        assertEquals(0L, paused.remainingTimeUnits());
        assertEquals(expiredAt + 100L, paused.lastProcessedTick());
        data.setPaused(false);
        assertEquals(List.of(target), MeltdownLifecycle.advance(data, expiredAt + 101L));
    }

    @Test
    void expiryAndWorkCompletionReturnExplicitTerminalReasons() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        List<UUID> expired = MeltdownLifecycle.advance(data,
            100L + MeltdownLifecycle.DEFAULT_DURATION_TICKS);
        assertEquals(List.of(target), expired);
        MeltdownResolution expiry = MeltdownLifecycle.end(data, target,
            MeltdownEndReason.EXPIRED);
        assertNotNull(expiry);
        assertEquals(MeltdownEndReason.EXPIRED, expiry.reason());
        assertTrue(data.getActiveMeltdowns().isEmpty());

        MeltdownLifecycle.start(data, target, 2000L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);
        MeltdownResolution work = MeltdownLifecycle.end(data, target,
            MeltdownEndReason.WORK_COMPLETED);
        assertNotNull(work);
        assertEquals(MeltdownEndReason.WORK_COMPLETED, work.reason());
    }

    @Test
    void batchSourceAndStageContextSurviveSaveReload() {
        FacilityDayData data = new FacilityDayData();
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        MeltdownStageContext context = new MeltdownStageContext(
            12, 3, true, "netzach", -1);
        MeltdownTargetLocator locator = new MeltdownTargetLocator(
            "lobotocraft:lobotomy_corporation", 123456L, true);

        MeltdownLifecycle.start(data, firstTarget, 100L, 3, "O-01",
            MeltdownCause.APOCALYPSE_BIRD_EGG, context, batchId, source, "small_egg", locator);
        MeltdownLifecycle.start(data, secondTarget, 100L, 3, "O-02",
            MeltdownCause.APOCALYPSE_BIRD_EGG, context, batchId, source, "small_egg", locator);

        FacilityDayData reloaded = FacilityDayData.load(data.save(new CompoundTag()));
        FacilityDayData.ActiveMeltdown first = reloaded.getActiveMeltdowns().get(firstTarget);
        FacilityDayData.ActiveMeltdown second = reloaded.getActiveMeltdowns().get(secondTarget);
        assertEquals(batchId, first.batchId());
        assertEquals(batchId, second.batchId());
        assertFalse(first.instanceId().equals(second.instanceId()));
        assertEquals(source, first.sourceEntity());
        assertEquals("small_egg", first.sourceType());
        assertEquals(context, first.stageContext());
        assertEquals(locator, first.targetLocator());
    }

    @Test
    void definitionIgnoresASecondMeltdownForTheSameTarget() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();
        assertTrue(MeltdownLifecycle.start(data, target, 100L, 2, "O-01",
            MeltdownCause.QLIPHOTH_OVERLOAD));
        UUID firstInstance = data.getActiveMeltdowns().get(target).instanceId();

        assertFalse(MeltdownLifecycle.start(data, target, 200L, 3, "O-01",
            MeltdownCause.APOCALYPSE_BIRD_EGG));

        assertEquals(firstInstance, data.getActiveMeltdowns().get(target).instanceId());
        assertEquals(MeltdownCause.QLIPHOTH_OVERLOAD,
            data.getActiveMeltdowns().get(target).cause());
    }
}
