package com.wzz.lobotocraft.employee;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.world.structure.StructureUpdater;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exercises the exact packet service seam from the captured server hang, not path search alone. */
@GameTestHolder(AreaMovePerformanceGameTests.NAMESPACE)
public final class AreaMovePerformanceGameTests {
    static final String NAMESPACE = "lobotocraft_area_move_performance";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 12000,
            batch = "area_move_batch_performance")
    public static void realBatchMoveDoesNotBlockServerAndAssignsAll64Employees(GameTestHelper helper) {
        boolean[] started = {false}, prepared = {false}, finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            ServerLevel level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (level == null) { finished[0] = true; helper.fail("missing facility"); return; }
            if (!started[0]) {
                started[0] = true;
                StructureUpdater.onPlayerEnter(level, null);
                return;
            }
            if (StructureUpdater.isRunning()) return;
            if (!Structures.LOBOTO.isGenerated(level)) {
                finished[0] = true;
                helper.fail("facility updater did not establish the generated state");
                return;
            }
            if (!prepared[0]) {
                prepared[0] = true;
                for (int cx = FacilityNavigationCache.COMPANY_MIN.getX() >> 4;
                     cx <= FacilityNavigationCache.COMPANY_MAX.getX() >> 4; cx++)
                    for (int cz = FacilityNavigationCache.COMPANY_MIN.getZ() >> 4;
                         cz <= FacilityNavigationCache.COMPANY_MAX.getZ() >> 4; cz++) level.getChunk(cx, cz);
                FacilityNavigationCache.prepare(level);
            }
            if (FacilityNavigationCache.staticCellCount(level) < 0) return;
            finished[0] = true;
            var progress = DepartmentProgressData.get(level.getServer());
            CompoundTag oldProgress = progress.save(new CompoundTag());
            var access = LlmDepartmentAccessData.get(level.getServer());
            CompoundTag oldAccess = access.save(new CompoundTag());
            var issuer = FakePlayerFactory.get(level,
                    new GameProfile(UUID.randomUUID(), "area_move_perf"));
            List<EmployeeRecord> employees = new ArrayList<>();
            try {
                progress.setLevel(EmployeeDepartment.CONTROL, 5);
                access.setAssignment(issuer.getUUID(), "area_move_perf", List.of(EmployeeDepartment.CONTROL));
                issuer.absMoveTo(195.5D, 270.0D, 52.5D, 0, 0);
                for (int i = 0; i < 64; i++) {
                    var record = EmployeeRuntimeManager.createAndSpawn(issuer,
                            "move_perf_" + i, EmployeeDepartment.CONTROL, 100, 100, 100, 100);
                    if (record == null) throw new IllegalStateException("failed to deploy employee " + i);
                    employees.add(record);
                }
                helper.assertTrue(FacilityNavigationCache.snapshot(level, FacilityNavigationCache.COMPANY_MIN,
                        FacilityNavigationCache.COMPANY_MAX) != null,
                        "spawning employees invalidated the full-facility snapshot");
                boolean legacy = "1".equals(System.getenv("LOBOTOCRAFT_AREA_MOVE_BASELINE"));
                boolean uncapped = "1".equals(System.getenv("LOBOTOCRAFT_AREA_MOVE_UNCAPPED"));
                long finalMillis = 0;
                for (int count : new int[]{1, 16, 64}) {
                    level.getRandom().setSeed(0x64C0FFEE);
                    long begin = System.nanoTime();
                    var result = legacy ? legacySamplingWorkload(level, employees.subList(0, count))
                            : FacilityAreaMoveService.dispatch(issuer,
                                    "dept/control/passage/sefira-malkuth-1",
                                    employees.subList(0, count).stream().map(EmployeeRecord::id).toList(),
                                    uncapped ? () -> 0L : System::nanoTime);
                    long millis = (System.nanoTime() - begin) / 1_000_000L;
                    com.mojang.logging.LogUtils.getLogger().info(
                            "Area move command measurement: legacyWorkload={}, uncapped={}, employees={}, assigned={}, rejected={}, elapsedMs={}",
                            legacy, uncapped, count, result.assigned(), result.rejected(), millis);
                    helper.assertTrue(result.assigned() == count && result.rejected() == 0,
                            "batch failed to assign safe targets to " + count + " employees: " + result);
                    if (count == 64) finalMillis = millis;
                    if (!legacy && count == 64) helper.assertTrue(employees.stream()
                                    .map(EmployeeRecord::manualStationPos).distinct().count() == 64,
                            "shared pool collapsed a roomy hall's employees onto duplicate primary targets");
                }
                helper.assertTrue(finalMillis < 100,
                        "64-employee command blocked server for " + finalMillis + "ms (budget 100ms)");
                if (!legacy) {
                    var player = EmployeeRuntimeManager.activePlayer(employees.get(0).id());
                    helper.assertTrue(FacilityAreaMoveService.targetValidator(level, player)
                                    .test(employees.get(0).manualStationPos()),
                            "idle sampling budget starved a proven safe main-room target");
                    var previousStations = employees.stream().map(EmployeeRecord::manualStationPos).toList();
                    var time = new java.util.concurrent.atomic.AtomicLong();
                    var budgetAbort = FacilityAreaMoveService.dispatch(issuer,
                            "dept/control/passage/sefira-malkuth-1",
                            employees.stream().map(EmployeeRecord::id).toList(),
                            () -> time.getAndAdd(1_000_000_000L));
                    helper.assertTrue(budgetAbort.assigned() == 0 && budgetAbort.rejected() == 64,
                            "expired budget accepted unvalidated targets or lost rejection counts");
                    helper.assertTrue(previousStations.equals(employees.stream()
                                    .map(EmployeeRecord::manualStationPos).toList())
                                    && employees.stream().allMatch(record -> record.taskType() == EmployeeTaskType.MOVE),
                            "budget abort changed existing employee orders/stations");
                }
                helper.succeed();
            } catch (Throwable failure) {
                helper.fail(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            } finally {
                for (var record : employees) EmployeeRuntimeManager.remove(level.getServer(), record);
                var storage = level.getServer().overworld().getDataStorage();
                storage.set("lobotocraft_department_progress", DepartmentProgressData.load(oldProgress));
                storage.set("lobotocraft_llm_department_access", LlmDepartmentAccessData.load(oldAccess));
            }
        });
    }

    /** Captured pre-fix hot path for measurements; opt-in, never used by the game.
     * Uses the real sampler and unchanged standability/1024-node checker. */
    private static FacilityAreaMoveService.Result legacySamplingWorkload(
            ServerLevel level, List<EmployeeRecord> employees) {
        var areas = com.wzz.lobotocraft.world.data.FacilityWorldLayout.targetAreas(
                level, "dept/control/passage/sefira-malkuth-1");
        java.util.Set<BlockPos> occupied = new java.util.HashSet<>();
        int assigned = 0;
        for (var record : employees) {
            var player = EmployeeRuntimeManager.activePlayer(record.id());
            List<BlockPos> targets = FacilityMoveTargetSampler.sampleCandidates(
                    areas, level.getRandom(), 128, 12,
                    pos -> !occupied.contains(pos) && FacilityAreaMoveService.isNavigableTarget(level, player, pos));
            if (targets.isEmpty()) targets = FacilityMoveTargetSampler.sampleCandidates(
                    areas, level.getRandom(), 128, 12,
                    pos -> FacilityAreaMoveService.isNavigableTarget(level, player, pos));
            if (!targets.isEmpty() && EmployeeRuntimeManager.assignManualMove(record, targets)) {
                occupied.add(targets.get(0));
                assigned++;
            }
        }
        return new FacilityAreaMoveService.Result(assigned, employees.size() - assigned,
                net.minecraft.network.chat.Component.empty());
    }
}
