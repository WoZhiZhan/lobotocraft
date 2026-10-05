package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.smiling_corpse_mountain.SmilingCorpseMountainBaseArmor;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.TimerEntry;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(WorkCloseRegressionGameTests.NAMESPACE)
public final class WorkCloseRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_work_close";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void blueStarDoesNotDecreaseCounterWhenWorkStarts(GameTestHelper helper) {
        EntityBlueStar star = spawn(helper, ModEntities.blue_star.get());
        EmployeeServerPlayer worker = employee(helper, 65);
        try {
            star.setQliphothCounter(1);
            helper.assertTrue(star.onWorkStart(worker, WorkType.INSIGHT), "work was rejected");
            helper.assertTrue(star.getQliphothCounter() == 1 && !star.hasEscape(),
                    "prudence IV work decreased the counter or escaped at work start");
            helper.succeed();
        } finally { cleanup(star, worker); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void blueStarDecreasesPrudenceCounterWhenTheRealSessionCompletes(GameTestHelper helper) {
        EntityBlueStar star = spawn(helper, ModEntities.blue_star.get());
        EmployeeServerPlayer worker = employee(helper, 65);
        try {
            star.setQliphothCounter(1);
            completeSession(helper, star, worker, 600);
            helper.assertTrue(star.getQliphothCounter() == 0 && star.hasEscape(),
                    "prudence IV was not settled at actual session completion");
            helper.assertFalse(WorkManager.isPlayerWorking(worker), "completed session remained active");
            // A stale cancel must not settle an already completed session again.
            WorkManager.cancelWork(worker, "stale cancellation");
            helper.assertTrue(star.getQliphothCounter() == 0, "stale session settled twice");
            helper.succeed();
        } finally { cleanup(star, worker); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void blueStarSixtySecondRuleIsCheckedAtCompletionNotDuringWork(GameTestHelper helper) {
        for (int duration : new int[]{1199, 1200, 1400}) {
            EntityBlueStar star = spawn(helper, ModEntities.blue_star.get());
            EmployeeServerPlayer worker = employee(helper, 85);
            try {
                WorkManager.WorkSession session = new WorkManager.WorkSession(
                        worker, star, WorkType.INSIGHT, false);
                session.workDuration = duration;
                star.onWorkTick(worker, session, WorkType.INSIGHT);
                helper.assertTrue(star.getQliphothCounter() == 2 && !session.forcedEnd,
                        "timeout was settled before work closed at " + duration + " ticks");
                completeSession(helper, star, worker, duration);
                helper.assertTrue(star.getQliphothCounter() == (duration < 1200 ? 2 : 1),
                        "incorrect 60-second completion threshold at " + duration + " ticks");
                helper.assertTrue(timerCount(star) == (duration < 1200 ? 0 : 1),
                        "timeout completion did not schedule exactly one execution scene");
            } finally { cleanup(star, worker); }
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void blueStarLongLowPrudenceWorkSettlesBothConditions(GameTestHelper helper) {
        EntityBlueStar star = spawn(helper, ModEntities.blue_star.get());
        EmployeeServerPlayer worker = employee(helper, 65);
        try {
            completeSession(helper, star, worker, 1200);
            helper.assertTrue(star.getQliphothCounter() == 0 && star.hasEscape(),
                    "timeout and prudence IV must each decrease once at completion");
            helper.assertTrue(timerCount(star) == 1, "execution was absent or duplicated");
            helper.succeed();
        } finally { cleanup(star, worker); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void corpseWorkerDeathSettlesOnceWhenTheSessionIsInterrupted(GameTestHelper helper) {
        EntitySmilingCorpseMountain corpse = spawn(helper, ModEntities.smiling_corpse_mountain.get());
        EmployeeServerPlayer worker = employee(helper, 85);
        try {
            activeWorks().put(worker.getUUID(), new WorkManager.WorkSession(
                    worker, corpse, WorkType.INSTINCT, false));
            worker.setHealth(0);
            WorkManager.onServerTick(new TickEvent.ServerTickEvent(
                    TickEvent.Phase.END, () -> true, helper.getLevel().getServer()));
            helper.assertTrue(corpse.getQliphothCounter() == 1,
                    "dead worker's real session exit did not decrease corpse mountain's counter");
            helper.assertFalse(WorkManager.isPlayerWorking(worker), "dead worker session remained active");
            WorkManager.cancelWork(worker, "duplicate death cleanup");
            helper.assertTrue(corpse.getQliphothCounter() == 1, "death exit settled twice");
            helper.succeed();
        } finally { cleanup(corpse, worker); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void corpseLivingCancellationDoesNotDecreaseCounter(GameTestHelper helper) {
        EntitySmilingCorpseMountain corpse = spawn(helper, ModEntities.smiling_corpse_mountain.get());
        EmployeeServerPlayer worker = employee(helper, 85);
        try {
            activeWorks().put(worker.getUUID(), new WorkManager.WorkSession(
                    worker, corpse, WorkType.INSTINCT, false));
            WorkManager.cancelWork(worker, "living worker leaves");
            helper.assertTrue(corpse.getQliphothCounter() == 2, "living cancellation counted as death");
            helper.succeed();
        } finally { cleanup(corpse, worker); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "work_close_regression")
    public static void corpseArmorDisplayMatchesTheActualBlackResistance(GameTestHelper helper) {
        EntitySmilingCorpseMountain corpse = spawn(helper, ModEntities.smiling_corpse_mountain.get());
        try {
            SmilingCorpseMountainBaseArmor armor =
                    (SmilingCorpseMountainBaseArmor) ModItems.SMILING_CORPSE_MOUNTAIN_CHESTPLATE.get();
            helper.assertTrue(corpse.getEGOArmorData().blackResistance() == 0.2F
                            && corpse.getEGOArmorData().blackResistance() == armor.getBlackResistance(),
                    "encyclopedia corpse armor BLACK resistance differs from the real armor's 0.2");
            helper.succeed();
        } finally { corpse.discard(); }
    }

    private static <T extends AbstractAbnormality> T spawn(GameTestHelper helper, EntityType<T> type) {
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 11; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        T abnormality = type.create(helper.getLevel());
        if (abnormality == null) throw new IllegalStateException("could not create fixture");
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 5));
        abnormality.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(abnormality);
        abnormality.recoverForNewFacilityDay();
        return abnormality;
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, int prudence) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), "work_close", EmployeeDepartment.CONTROL);
        record.setStats(100, prudence, 100, 100);
        EmployeeServerPlayer worker = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        BlockPos pos = helper.absolutePos(new BlockPos(7, 1, 5));
        worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        worker.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).orElseThrow(
                () -> new IllegalStateException("employee stats missing")).setPrudence(prudence);
        MentalValueUtil.setMentalValue(worker, MentalValueUtil.getEffectiveMaxMentalValue(worker));
        return worker;
    }

    private static void completeSession(GameTestHelper helper, AbstractAbnormality abnormality,
                                        EmployeeServerPlayer worker, int duration) {
        WorkManager.WorkSession session = new WorkManager.WorkSession(worker, abnormality, WorkType.INSIGHT, false);
        session.workDuration = duration;
        activeWorks().put(worker.getUUID(), session);
        WorkManager.forceCompleteWork(worker, WorkResult.NORMAL, "regression completion");
        WorkManager.onServerTick(new TickEvent.ServerTickEvent(
                TickEvent.Phase.END, () -> true, helper.getLevel().getServer()));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, WorkManager.WorkSession> activeWorks() {
        try {
            Field field = WorkManager.class.getDeclaredField("activeWorks");
            field.setAccessible(true);
            return (Map<UUID, WorkManager.WorkSession>) field.get(null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static int timerCount(EntityBlueStar star) {
        try {
            Field field = EntityBlueStar.class.getDeclaredField("activeEscapeTimers");
            field.setAccessible(true);
            return ((Set<?>) field.get(star)).size();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void cleanup(AbstractAbnormality abnormality, EmployeeServerPlayer worker) {
        WorkManager.cancelWork(worker, "fixture cleanup");
        TimerEntry.shutdown(abnormality);
        abnormality.discard();
        worker.discard();
    }
}
