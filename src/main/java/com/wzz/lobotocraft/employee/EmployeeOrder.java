package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

final class EmployeeOrder {
    final EmployeeTaskType type;
    BlockPos targetPos;
    final List<BlockPos> moveTargets;
    int moveTargetIndex;
    final UUID targetEntity;
    /** Slot selected in the UI or by the LLM. */
    final WorkType workType;
    /** Actual work type fixed at order time during Malkuth suppression. */
    final WorkType resolvedWorkType;
    final boolean returnToManualStation;
    boolean started;

    private EmployeeOrder(EmployeeTaskType type, BlockPos targetPos, List<BlockPos> moveTargets,
                          UUID targetEntity,
                          WorkType workType, WorkType resolvedWorkType,
                          boolean returnToManualStation) {
        this.type = type;
        this.targetPos = targetPos;
        this.moveTargets = moveTargets;
        this.targetEntity = targetEntity;
        this.workType = workType;
        this.resolvedWorkType = resolvedWorkType;
        this.returnToManualStation = returnToManualStation;
    }

    static EmployeeOrder move(BlockPos target) {
        return move(List.of(target));
    }

    static EmployeeOrder move(List<BlockPos> targets) {
        List<BlockPos> immutableTargets = targets.stream()
                .filter(java.util.Objects::nonNull)
                .map(BlockPos::immutable)
                .distinct()
                .toList();
        if (immutableTargets.isEmpty()) throw new IllegalArgumentException("move targets are empty");
        return new EmployeeOrder(EmployeeTaskType.MOVE, immutableTargets.get(0), immutableTargets,
                null,
                null, null, false);
    }

    boolean advanceMoveTarget() {
        if (moveTargetIndex + 1 >= moveTargets.size()) return false;
        targetPos = moveTargets.get(++moveTargetIndex);
        return true;
    }

    boolean hasMoveTargetAlternative() {
        return moveTargetIndex + 1 < moveTargets.size();
    }

    static EmployeeOrder work(UUID abnormality, WorkType workType, WorkType resolvedWorkType,
                              boolean returnToManualStation) {
        return new EmployeeOrder(EmployeeTaskType.WORK, null, List.of(), abnormality,
                workType, resolvedWorkType,
                returnToManualStation);
    }

    static EmployeeOrder toolUse(UUID abnormality) {
        return new EmployeeOrder(EmployeeTaskType.TOOL_USE, null, List.of(), abnormality,
                null, null, false);
    }

    static EmployeeOrder suppress(UUID target) {
        return new EmployeeOrder(EmployeeTaskType.SEARCH_AND_SUPPRESS, null, List.of(), target,
                null, null, false);
    }

    static EmployeeOrder follow(UUID target) {
        return new EmployeeOrder(EmployeeTaskType.FOLLOW, null, List.of(), target,
                null, null, false);
    }
}
