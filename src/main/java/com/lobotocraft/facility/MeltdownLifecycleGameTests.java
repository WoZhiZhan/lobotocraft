package com.lobotocraft.facility;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownCause;
import com.lobotocraft.facility.data.MeltdownEndReason;
import com.lobotocraft.facility.data.MeltdownLifecycle;
import com.lobotocraft.facility.network.FacilityStatePacket;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.entity.abnormality.EntityOneBad;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(MeltdownLifecycleGameTests.TEST_NAMESPACE)
public final class MeltdownLifecycleGameTests {
    public static final String TEST_NAMESPACE = ModMain.MODID + "_meltdown";
    private static final String TEMPLATE = "navigation_empty";
    private static final String BATCH = "meltdown_lifecycle";

    private MeltdownLifecycleGameTests() {
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 20, required = true, batch = BATCH)
    public static void ordinaryDayKeepsQliphothAndBirdEggMeltdowns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FacilityDayData data = reset(level);
        UUID qliphothTarget = UUID.randomUUID();
        UUID birdTarget = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();

        try {
            helper.assertFalse(CoreSuppressionData.get(level).isActive(),
                "test server unexpectedly started with an active core suppression");
            MeltdownLifecycle.start(data, qliphothTarget, now, 2, "O-01",
                MeltdownCause.QLIPHOTH_OVERLOAD);
            MeltdownLifecycle.start(data, birdTarget, now, 2, "O-02",
                MeltdownCause.APOCALYPSE_BIRD_EGG);

            FacilityManager.tick(level.getServer());

            helper.assertTrue(data.getActiveMeltdowns().containsKey(qliphothTarget),
                "ordinary server tick removed a normal Qliphoth meltdown");
            helper.assertTrue(data.getActiveMeltdowns().containsKey(birdTarget),
                "ordinary server tick incorrectly removed the bird-egg meltdown");
            helper.succeed();
        } finally {
            MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 20, required = true, batch = "meltdown_facility_pause")
    public static void facilityPauseRebasesRealManagerClockBeforeResuming(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        FacilityDayData data = FacilityDayData.get(overworld);
        Map<UUID, FacilityDayData.ActiveMeltdown> previous = new HashMap<>(data.getActiveMeltdowns());
        boolean previouslyPaused = data.isPaused();
        long now = overworld.getGameTime();
        UUID target = UUID.randomUUID();
        try {
            data.getActiveMeltdowns().clear();
            data.setPaused(false);
            helper.assertTrue(MeltdownLifecycle.start(data, target, now, 2, "O-03-89",
                    MeltdownCause.APOCALYPSE_BIRD_EGG), "could not start the pause fixture");
            MeltdownLifecycle.setTimeRate(data, target, now, 2000);
            overworld.serverLevelData.setGameTime(now + 10L);
            FacilityManager.tick(overworld.getServer());
            long budget = data.getActiveMeltdowns().get(target).remainingTimeUnits();
            helper.assertTrue(budget == (long) MeltdownLifecycle.DEFAULT_DURATION_TICKS
                    * FacilityDayData.ActiveMeltdown.TIME_UNITS_PER_TICK - 20_000L,
                    "the real manager did not advance the unpaused fixture at double rate");
            data.setPaused(true);
            Map<UUID, Integer> snapshot = FacilityStatePacket.snapshotMeltdowns(data, now + 10L);

            for (long offset : new long[]{110L, 310L}) {
                overworld.serverLevelData.setGameTime(now + offset);
                FacilityManager.tick(overworld.getServer());
                FacilityDayData.ActiveMeltdown paused = data.getActiveMeltdowns().get(target);
                helper.assertTrue(paused != null, "the paused manager removed the meltdown");
                helper.assertTrue(paused.lastProcessedTick() == now + offset,
                        "FacilityManager's pause branch did not rebase the meltdown clock");
                helper.assertTrue(paused.remainingTimeUnits() == budget,
                        "the paused manager consumed meltdown budget");
                helper.assertTrue(paused.timeRatePermille() == 2000,
                        "facility pause replaced the configured meltdown rate");
                helper.assertTrue(snapshot.equals(FacilityStatePacket.snapshotMeltdowns(data, now + offset)),
                        "the authoritative countdown changed while the facility was paused");
            }

            data.setPaused(false);
            overworld.serverLevelData.setGameTime(now + 311L);
            FacilityManager.tick(overworld.getServer());
            FacilityDayData.ActiveMeltdown resumed = data.getActiveMeltdowns().get(target);
            helper.assertTrue(resumed != null && resumed.remainingTimeUnits() == budget - 2000L
                            && resumed.lastProcessedTick() == now + 311L
                            && resumed.timeRatePermille() == 2000,
                    "the resumed manager charged paused ticks or replaced the configured rate");
            helper.succeed();
        } finally {
            overworld.serverLevelData.setGameTime(now);
            data.getActiveMeltdowns().clear();
            data.getActiveMeltdowns().putAll(previous);
            data.setPaused(previouslyPaused);
            data.setDirty();
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 30, required = true, batch = BATCH)
    public static void pausedServerClockResumesWithoutChargingPausedTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FacilityDayData data = reset(level);
        UUID target = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        MeltdownLifecycle.start(data, target, now, 2, "O-02",
            MeltdownCause.APOCALYPSE_BIRD_EGG);
        MeltdownLifecycle.setTimeRate(data, target, now, 0);
        long pausedRemaining = data.getActiveMeltdowns().get(target).remainingTimeUnits();

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(data.getActiveMeltdowns().containsKey(target),
                "paused bird-egg meltdown disappeared on the server");
            helper.assertTrue(data.getActiveMeltdowns().get(target).remainingTimeUnits()
                    == pausedRemaining,
                "paused meltdown consumed time");
            MeltdownLifecycle.setTimeRate(data, target,
                level.getServer().overworld().getGameTime(),
                MeltdownLifecycle.NORMAL_TIME_RATE_PERMILLE);
        });

        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(data.getActiveMeltdowns().containsKey(target),
                    "resumed meltdown expired far earlier than its definition duration");
                helper.assertTrue(data.getActiveMeltdowns().get(target).remainingTimeUnits()
                        < pausedRemaining,
                    "resumed meltdown did not advance on server ticks");
                helper.assertTrue(MeltdownLifecycle.resolve(data, target),
                    "work-completion lifecycle entry did not resolve the active meltdown");
                helper.assertFalse(data.getActiveMeltdowns().containsKey(target),
                    "resolved meltdown remained in server SavedData");
                helper.succeed();
            } finally {
                MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
            }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 100, required = true, batch = BATCH)
    public static void independentMeltdownExpiresOnOrdinaryServerTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FacilityDayData data = reset(level);
        UUID target = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        MeltdownLifecycle.start(data, target, now, 2, "",
            MeltdownCause.APOCALYPSE_BIRD_EGG);
        MeltdownLifecycle.setTimeRate(data, target, now,
            MeltdownLifecycle.MAX_TIME_RATE_PERMILLE);

        helper.runAfterDelay(80, () -> {
            try {
                helper.assertFalse(data.getActiveMeltdowns().containsKey(target),
                    "independent meltdown did not expire during an ordinary server day");
                helper.succeed();
            } finally {
                MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
            }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = BATCH)
    public static void centralizedMigrationClearsRetiredEntityMeltdownTags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityOneBad abnormality = ModEntities.onebad.get().create(level);
        if (abnormality == null) {
            helper.fail("One Sin could not be created for legacy meltdown migration");
            return;
        }
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 1, 2)));
        abnormality.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        abnormality.getPersistentData().putBoolean("lobotocraft:CoreMeltdownActive", true);
        abnormality.getPersistentData().putLong("lobotocraft:CoreMeltdownDeadline", Long.MAX_VALUE);
        abnormality.getPersistentData().putBoolean("lobotocraft:CoreMeltdownPreviousGlow", false);
        abnormality.setGlowingTag(true);
        level.addFreshEntity(abnormality);

        helper.runAfterDelay(25, () -> {
            try {
                helper.assertFalse(abnormality.getPersistentData().contains(
                        "lobotocraft:CoreMeltdownActive"),
                    "centralized migration left the retired active tag on the entity");
                helper.assertFalse(abnormality.getPersistentData().contains(
                        "lobotocraft:CoreMeltdownDeadline"),
                    "centralized migration left the retired deadline tag on the entity");
                helper.assertFalse(abnormality.isCurrentlyGlowing(),
                    "centralized migration did not restore the pre-meltdown glow state");
                helper.succeed();
            } finally {
                abnormality.discard();
            }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "meltdown_original_targeting")
    public static void ordinaryMeltdownCanTargetAnAbnormalityAlreadyBeingWorked(
            GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        FacilityDayData data = reset(facility);
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityOneBad abnormality = ModEntities.onebad.get().create(facility);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;
        if (abnormality == null || marker == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("working-target containment fixture could not be created");
            return;
        }
        abnormality.absMoveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(abnormality);
        if (!marker.bindByUuid(facility, abnormality.getUUID())) {
            abnormality.discard();
            clearContainmentFixture(facility, markerPos);
            helper.fail("room marker rejected the working meltdown target");
            return;
        }

        ServerPlayer worker = FakePlayerFactory.get(facility,
                new GameProfile(UUID.randomUUID(), "meltdown_working_target"));
        worker.absMoveTo(markerPos.getX() + 1.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        worker.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(100);
            stats.setPrudence(100);
            stats.setTemperance(100);
            stats.setJustice(100);
        });

        data.debugSetMeltdownGauge(0);
        helper.assertTrue(WorkManager.startWork(worker, abnormality, WorkType.INSTINCT),
                "test target did not enter a real work session");
        helper.assertTrue(WorkManager.isAbnormalityBeingWorked(abnormality),
                "work manager did not retain the active session");
        helper.assertTrue(ordinaryMeltdownCandidates(facility, data).contains(abnormality),
                "ordinary meltdown excluded an actively worked abnormality");
        helper.assertTrue(MeltdownLifecycle.start(data, abnormality.getUUID(),
                        facility.getServer().overworld().getGameTime(), 2,
                        abnormality.getAbnormalityCode(), MeltdownCause.QLIPHOTH_OVERLOAD),
                "test could not apply a meltdown during active work");
        FacilityManager.tick(facility.getServer());
        long pausedBudget = data.getActiveMeltdowns().get(abnormality.getUUID())
                .remainingTimeUnits();

        helper.runAfterDelay(5, () -> {
            try {
                FacilityDayData.ActiveMeltdown active =
                        data.getActiveMeltdowns().get(abnormality.getUUID());
                helper.assertTrue(active != null,
                        "meltdown vanished while the earlier work was still active");
                helper.assertTrue(active.remainingTimeUnits() == pausedBudget,
                        "meltdown timer advanced while its target was being worked");

                WorkManager.cancelWork(worker, "GameTest prepare overload-resolution work");
                int gaugeBeforeResolution = data.getMeltdownGauge();
                helper.assertTrue(WorkManager.startWork(worker, abnormality, WorkType.INSTINCT),
                        "could not start work used to resolve an existing meltdown");
                helper.assertFalse(data.getActiveMeltdowns().containsKey(abnormality.getUUID()),
                        "work start did not immediately resolve the existing meltdown");
                helper.assertTrue(data.getMeltdownGauge() == gaugeBeforeResolution,
                        "meltdown-resolution work advanced the facility gauge");
                helper.succeed();
            } finally {
                WorkManager.cancelWork(worker, "GameTest original meltdown targeting cleanup");
                abnormality.discard();
                MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
                clearContainmentFixture(facility, markerPos);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static List<AbstractAbnormality> ordinaryMeltdownCandidates(
            ServerLevel level, FacilityDayData data) {
        try {
            Method collect = FacilityManager.class.getDeclaredMethod(
                    "collectMeltdownCandidates", ServerLevel.class,
                    FacilityDayData.class, MeltdownCause.class);
            collect.setAccessible(true);
            Object pool = collect.invoke(null, level, data, MeltdownCause.QLIPHOTH_OVERLOAD);
            Method targets = pool.getClass().getDeclaredMethod("availableTargets");
            targets.setAccessible(true);
            return (List<AbstractAbnormality>) targets.invoke(pool);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to inspect ordinary meltdown candidates",
                    exception);
        }
    }

    private static void prepareContainmentFixture(ServerLevel level, BlockPos markerPos) {
        level.getChunkAt(markerPos);
        for (AbstractAbnormality existing : level.getEntitiesOfClass(
                AbstractAbnormality.class, new AABB(markerPos).inflate(8.0D, 5.0D, 8.0D))) {
            existing.discard();
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(markerPos.offset(dx, -1, dz),
                        Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(markerPos, ModBlocks.ROOM_MARKER.get().defaultBlockState());
    }

    private static void clearContainmentFixture(ServerLevel level, BlockPos markerPos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static FacilityDayData reset(ServerLevel level) {
        FacilityDayData data = FacilityDayData.get(level);
        MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
        return data;
    }
}
