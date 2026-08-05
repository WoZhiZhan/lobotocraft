package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationCandidatePolicy;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.employee.navigation.NodeEvaluator;
import com.wzz.lobotocraft.employee.navigation.TransitionGenerator;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative monitor command that scatters selected employees inside one logical area. */
public final class FacilityAreaMoveService {

    public static final int MAX_EMPLOYEES = 64;
    private static final int SAMPLE_ATTEMPTS = MAX_EMPLOYEES * 2;
    private static final int TARGET_ALTERNATIVES = 12;

    private FacilityAreaMoveService() {
    }

    public static Result dispatch(ServerPlayer issuer, String groupId, Collection<UUID> requestedIds) {
        if (issuer.serverLevel().dimension() != ModDimensions.LOBOTO_KEY) {
            return Result.failure("请回到公司后再调度员工");
        }
        if (groupId == null || groupId.isBlank() || requestedIds == null || requestedIds.isEmpty()) {
            return Result.failure("没有有效的移动目标或员工");
        }

        ServerLevel level = issuer.serverLevel();
        if (!FacilityAccessService.isGroupOpen(
                issuer.server, level.dimension().location(), groupId)) {
            return Result.failure("\u8be5\u533a\u57df\u5c1a\u672a\u5f00\u653e");
        }
        List<FacilityWorldLayout.AreaDefinition> targetAreas =
                FacilityWorldLayout.targetAreas(level, groupId);
        if (targetAreas.isEmpty()) return Result.failure("该监控区域尚未绑定现实房间");
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() > MAX_EMPLOYEES) return Result.failure("一次最多调度 64 名员工");

        FacilityPersonnelData personnel = FacilityPersonnelData.get(issuer.server);
        LlmDepartmentAccessData access = LlmDepartmentAccessData.get(issuer.server);
        RandomSource random = level.getRandom();
        Set<BlockPos> occupiedTargets = new HashSet<>();
        int assigned = 0;
        int rejected = 0;
        String firstFailure = "";

        for (UUID employeeId : uniqueIds) {
            EmployeeRecord record = personnel.get(employeeId);
            String invalid = validateEmployee(issuer, record, access);
            if (invalid != null) {
                rejected++;
                if (firstFailure.isEmpty()) firstFailure = invalid;
                continue;
            }
            EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(employeeId);
            List<BlockPos> targets = FacilityMoveTargetSampler.sampleCandidates(
                    targetAreas, random, SAMPLE_ATTEMPTS, TARGET_ALTERNATIVES,
                    candidate -> !occupiedTargets.contains(candidate)
                            && isNavigableTarget(level, employee, candidate));
            if (targets.isEmpty()) {
                targets = FacilityMoveTargetSampler.sampleCandidates(
                        targetAreas, random, SAMPLE_ATTEMPTS, TARGET_ALTERNATIVES,
                        candidate -> isNavigableTarget(level, employee, candidate));
            }
            if (targets.isEmpty() || !EmployeeRuntimeManager.assignManualMove(record, targets)) {
                rejected++;
                if (firstFailure.isEmpty()) firstFailure = "区域内没有可到达的落脚点";
                continue;
            }
            occupiedTargets.add(targets.get(0));
            assigned++;
        }

        if (assigned == 0) return Result.failure(firstFailure.isEmpty() ? "没有员工接受命令" : firstFailure);
        String message = rejected == 0
                ? "已向 " + assigned + " 名员工下达移动命令"
                : "已调度 " + assigned + " 名员工，" + rejected + " 名未接受";
        return new Result(assigned, rejected, message);
    }

    private static String validateEmployee(ServerPlayer issuer, EmployeeRecord record,
                                           LlmDepartmentAccessData access) {
        if (record == null || record.retired()) return "员工不存在";
        if (!DepartmentProgressService.isSectionOpen(
                issuer.server, record.department(), record.centralSection())) {
            return "员工所属区域尚未开放";
        }
        if (!access.canManage(issuer, record.department())) return "你无权调度该部门";
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
        if (employee == null || !employee.isAlive() || employee.isRemoved()) return "员工未部署";
        if (employee.serverLevel() != issuer.serverLevel()) return "员工不在公司内";
        if (WorkManager.isPlayerWorking(employee)) {
            return "工作中的员工不能接受移动命令";
        }
        boolean panicking = MentalValueUtil.isPanic(employee);
        return panicking ? "精神崩溃的员工无法接受命令" : null;
    }

    static boolean isStandable(ServerLevel level, EmployeeServerPlayer employee, BlockPos candidate) {
        return standingY(level, employee, candidate) != null;
    }

    static boolean isNavigableTarget(ServerLevel level, EmployeeServerPlayer employee,
                                     BlockPos candidate) {
        Double y = standingY(level, employee, candidate);
        if (y == null) return false;
        BlockPos minimum = candidate.offset(-2, -2, -2);
        BlockPos maximum = candidate.offset(2, 3, 2);
        NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(level, minimum, maximum);
        if (snapshot == null) {
            snapshot = NavigationSnapshot.capture(level, minimum, maximum, 150);
        }
        NodeEvaluator evaluator = new NodeEvaluator(snapshot,
                employee.getBbWidth(), employee.getBbHeight());
        NavigationNode node = new NavigationNode(candidate, y);
        if (!evaluator.evaluate(node, false).traversable()) return false;
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        return NavigationCandidatePolicy.hasLocalMobility(node, transitions::generate);
    }

    private static Double standingY(ServerLevel level, EmployeeServerPlayer employee,
                                    BlockPos candidate) {
        if (!level.getWorldBorder().isWithinBounds(candidate)) return null;
        // Movement sampling must not turn an unreachable or command-injected
        // coordinate into a synchronous chunk load.
        if (!level.hasChunkAt(candidate)) return null;
        double standingY = candidate.getY();
        VoxelShape localShape = level.getBlockState(candidate)
                .getCollisionShape(level, candidate);
        if (!localShape.isEmpty()) {
            double surface = localShape.bounds().maxY;
            // Carpet, thin snow and similar floor dressings share the feet block.
            if (surface <= 0.25D) standingY += surface;
        }
        Vec3 standing = new Vec3(candidate.getX() + 0.5D, standingY,
                candidate.getZ() + 0.5D);
        AABB box = employee.getDimensions(employee.getPose())
                .makeBoundingBox(standing.x, standing.y, standing.z);
        if (!level.noCollision(employee, box)) return null;
        double halfWidth = employee.getBbWidth() / 2.0D;
        AABB support = new AABB(standing.x - halfWidth, standing.y - 0.0625D,
                standing.z - halfWidth, standing.x + halfWidth,
                standing.y + 1.0E-6D, standing.z + halfWidth);
        return level.getBlockCollisions(employee, support).iterator().hasNext()
                ? standingY : null;
    }

    public record Result(int assigned, int rejected, String message) {
        public static Result failure(String message) {
            return new Result(0, 0, message);
        }

        public boolean successful() {
            return assigned > 0;
        }
    }
}
