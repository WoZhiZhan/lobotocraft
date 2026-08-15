package com.lobotocraft.facility;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownCause;
import com.lobotocraft.facility.data.MeltdownEndReason;
import com.lobotocraft.facility.data.MeltdownLifecycle;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.entity.abnormality.EntityOneBad;
import com.wzz.lobotocraft.init.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
    public static void ordinaryDayKeepsBirdEggAndEndsSuppressionMeltdown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FacilityDayData data = reset(level);
        UUID suppressionTarget = UUID.randomUUID();
        UUID birdTarget = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();

        try {
            helper.assertFalse(CoreSuppressionData.get(level).isActive(),
                "test server unexpectedly started with an active core suppression");
            MeltdownLifecycle.start(data, suppressionTarget, now, 2, "O-01",
                MeltdownCause.QLIPHOTH_OVERLOAD);
            MeltdownLifecycle.start(data, birdTarget, now, 2, "O-02",
                MeltdownCause.APOCALYPSE_BIRD_EGG);

            FacilityManager.tick(level.getServer());

            helper.assertFalse(data.getActiveMeltdowns().containsKey(suppressionTarget),
                "ordinary server tick retained a core-suppression-only meltdown");
            helper.assertTrue(data.getActiveMeltdowns().containsKey(birdTarget),
                "ordinary server tick incorrectly removed the bird-egg meltdown");
            helper.succeed();
        } finally {
            MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
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

    private static FacilityDayData reset(ServerLevel level) {
        FacilityDayData data = FacilityDayData.get(level);
        MeltdownLifecycle.clear(data, MeltdownEndReason.LEGACY_MIGRATION);
        return data;
    }
}
