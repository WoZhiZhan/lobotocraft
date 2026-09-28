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
import net.minecraft.network.chat.Component;
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
            return Result.failure("message.lobotocraft.area_move.failure.company_only");
        }
        if (groupId == null || groupId.isBlank() || requestedIds == null || requestedIds.isEmpty()) {
            return Result.failure("message.lobotocraft.area_move.failure.invalid_target");
        }

        ServerLevel level = issuer.serverLevel();
        if (!FacilityAccessService.isGroupOpen(
                issuer.server, level.dimension().location(), groupId)) {
            return Result.failure("message.lobotocraft.area_move.failure.area_closed");
        }
        List<FacilityWorldLayout.AreaDefinition> targetAreas =
                FacilityWorldLayout.targetAreas(level, groupId);
        if (targetAreas.isEmpty()) {
            return Result.failure("message.lobotocraft.area_move.failure.unbound");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() > MAX_EMPLOYEES) {
            return Result.failure("message.lobotocraft.area_move.failure.too_many", MAX_EMPLOYEES);
        }

        FacilityPersonnelData personnel = FacilityPersonnelData.get(issuer.server);
        LlmDepartmentAccessData access = LlmDepartmentAccessData.get(issuer.server);
        RandomSource random = level.getRandom();
        Set<BlockPos> occupiedTargets = new HashSet<>();
        int assigned = 0;
        int rejected = 0;
        Component firstFailure = null;

        for (UUID employeeId : uniqueIds) {
            EmployeeRecord record = personnel.get(employeeId);
            Component invalid = validateEmployee(issuer, record, access);
            if (invalid != null) {
                rejected++;
                if (firstFailure == null) firstFailure = invalid;
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
                if (firstFailure == null) {
                    firstFailure = Component.translatable(
                            "message.lobotocraft.area_move.failure.no_reachable_target");
                }
                continue;
            }
            occupiedTargets.add(targets.get(0));
            assigned++;
        }

        if (assigned == 0) {
            return Result.failure(firstFailure == null
                    ? Component.translatable("message.lobotocraft.area_move.failure.none_accepted")
                    : firstFailure);
        }
        Component message = rejected == 0
                ? Component.translatable("message.lobotocraft.area_move.success", assigned)
                : Component.translatable("message.lobotocraft.area_move.partial", assigned, rejected);
        return new Result(assigned, rejected, message);
    }

    private static Component validateEmployee(ServerPlayer issuer, EmployeeRecord record,
                                              LlmDepartmentAccessData access) {
        if (record == null || record.retired()) {
            return Component.translatable("message.lobotocraft.area_move.failure.employee_missing");
        }
        if (!DepartmentProgressService.isSectionOpen(
                issuer.server, record.department(), record.centralSection())) {
            return Component.translatable("message.lobotocraft.area_move.failure.employee_area_closed");
        }
        if (!access.canManage(issuer, record.department())) {
            return Component.translatable("message.lobotocraft.area_move.failure.department_permission");
        }
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
        if (employee == null || !employee.isAlive() || employee.isRemoved()) {
            return Component.translatable("message.lobotocraft.area_move.failure.employee_undeployed");
        }
        if (employee.serverLevel() != issuer.serverLevel()) {
            return Component.translatable("message.lobotocraft.area_move.failure.employee_outside");
        }
        if (WorkManager.isPlayerWorking(employee)) {
            return Component.translatable("message.lobotocraft.area_move.failure.employee_working");
        }
        boolean panicking = MentalValueUtil.isPanic(employee);
        return panicking
                ? Component.translatable("message.lobotocraft.area_move.failure.employee_panicking")
                : null;
    }

    static boolean isStandable(ServerLevel level, EmployeeServerPlayer employee, BlockPos candidate) {
        return standingY(level, employee, candidate) != null;
    }

    static boolean isNavigableTarget(ServerLevel level, EmployeeServerPlayer employee,
                                     BlockPos candidate) {
        Double y = standingY(level, employee, candidate);
        if (y == null) return false;
        // Company-wide bounds cost nothing upfront on the shared cache (cells
        // resolve lazily) and let the region probe distinguish a corridor-
        // connected surface from a sealed island by exhaustion instead of by
        // snapshot clipping.
        NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(level,
                FacilityNavigationCache.COMPANY_MIN, FacilityNavigationCache.COMPANY_MAX);
        boolean facilityWide = snapshot != null;
        if (!facilityWide) {
            snapshot = NavigationSnapshot.capture(level,
                    candidate.offset(-2, -2, -2), candidate.offset(2, 3, 2), 150);
        }
        NodeEvaluator evaluator = new NodeEvaluator(snapshot,
                employee.getBbWidth(), employee.getBbHeight());
        NavigationNode node = new NavigationNode(candidate, y);
        if (!evaluator.evaluate(node, false).traversable()) return false;
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        return facilityWide
                ? NavigationCandidatePolicy.isFacilityConnectedRegion(node, transitions::generate)
                : NavigationCandidatePolicy.hasLocalMobility(node, transitions::generate);
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

    public record Result(int assigned, int rejected, Component message) {
        public static Result failure(String key, Object... arguments) {
            return failure(Component.translatable(key, arguments));
        }

        public static Result failure(Component message) {
            return new Result(0, 0, message);
        }

        public boolean successful() {
            return assigned > 0;
        }
    }
}
