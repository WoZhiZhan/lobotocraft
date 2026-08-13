package com.wzz.lobotocraft.employee;

import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RoomEmployeeSnapshotService {
    private RoomEmployeeSnapshotService() {
    }

    public static List<RoomEmployeeSnapshot> collect(ServerPlayer viewer) {
        return collect(viewer, viewer.serverLevel().dimension().location());
    }

    public static List<RoomEmployeeSnapshot> collect(ServerPlayer viewer, ResourceLocation dimension) {
        List<RoomEmployeeSnapshot> result = collectEmployees(viewer, null, null, false, dimension);
        for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
            if (player instanceof EmployeeServerPlayer || player.isRemoved() || !player.isAlive()
                    || !player.serverLevel().dimension().location().equals(dimension)) continue;
            if (!FacilityAccessService.isPositionOpen(viewer.server, dimension,
                    player.blockPosition())) continue;
            result.add(playerSnapshot(player));
        }
        sort(result);
        return List.copyOf(result);
    }

    public static List<RoomEmployeeSnapshot> collect(ServerPlayer viewer,
                                                       AbstractAbnormality abnormality,
                                                       WorkType workType) {
        List<RoomEmployeeSnapshot> result = collectEmployees(viewer, abnormality, workType, true);
        sort(result);
        return List.copyOf(result);
    }

    public static List<RoomEmployeeSnapshot> collectForSuppression(ServerPlayer viewer,
                                                                    AbstractAbnormality abnormality) {
        List<RoomEmployeeSnapshot> result = collectEmployees(viewer, abnormality, null, true);
        sort(result);
        return List.copyOf(result);
    }

    private static List<RoomEmployeeSnapshot> collectEmployees(ServerPlayer viewer,
                                                                AbstractAbnormality abnormality,
                                                                WorkType workType,
                                                                boolean candidatesOnly) {
        return collectEmployees(viewer, abnormality, workType, candidatesOnly, null);
    }

    private static List<RoomEmployeeSnapshot> collectEmployees(ServerPlayer viewer,
                                                                AbstractAbnormality abnormality,
                                                                WorkType workType,
                                                                boolean candidatesOnly,
                                                                ResourceLocation dimension) {
        List<RoomEmployeeSnapshot> result = new ArrayList<>();
        LlmDepartmentAccessData access = LlmDepartmentAccessData.get(viewer.server);
        for (EmployeeRecord record : FacilityPersonnelData.get(viewer.server).employees()) {
            if (record.retired() || !DepartmentProgressService.isSectionOpen(viewer.server,
                    record.department(), record.centralSection())) continue;
            boolean commandable = access.canManage(viewer, record.department());
            if (candidatesOnly && !commandable) continue;
            EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
            if (employee == null || employee.isRemoved() || !employee.isAlive()) continue;
            if (dimension != null
                    && !employee.serverLevel().dimension().location().equals(dimension)) continue;
            result.add(snapshot(viewer, record, employee, abnormality, workType, commandable));
        }
        return result;
    }

    private static void sort(List<RoomEmployeeSnapshot> result) {
        result.sort(Comparator
                .comparing((RoomEmployeeSnapshot entry) -> entry.departmentId())
                .thenComparing(RoomEmployeeSnapshot::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoomEmployeeSnapshot::id));
    }

    private static RoomEmployeeSnapshot snapshot(ServerPlayer viewer, EmployeeRecord record,
                                                  EmployeeServerPlayer employee,
                                                  AbstractAbnormality abnormality, WorkType workType,
                                                  boolean commandable) {
        float[] mental = {0.0F, 0.0F};
        employee.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(value -> {
            mental[0] = value.getMentalValue();
            mental[1] = value.getEffectiveMaxMentalValue();
        });
        int[] stats = {record.fortitude(), record.prudence(), record.temperance(), record.justice(),
                EmployeeLevelCalculator.fromStatValues(record.fortitude(), record.prudence(),
                        record.temperance(), record.justice())};
        employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(value -> {
            stats[0] = value.getFortitude();
            stats[1] = value.getPrudence();
            stats[2] = value.getTemperance();
            stats[3] = value.getJustice();
            stats[4] = value.getEmployeeLevel();
        });

        boolean eligible = true;
        boolean panicking = MentalValueUtil.isPanic(employee);
        String reason = "";
        float successRate = -1.0F;
        int estimatedSeconds = 0;
        boolean malkuthScramble = CoreSuppressionManager.isMalkuthWorkScrambled(viewer.level());
        boolean workInfoUnlocked = abnormality == null || workType == null;
        if (!workInfoUnlocked) {
            workInfoUnlocked = !malkuthScramble
                    && viewer.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                    .map(data -> data.isManualUnlocked(abnormality.getAbnormalityCode(), 100 + workType.ordinal()))
                    .orElse(false);
        }
        if (abnormality != null) {
            boolean suppression = workType == null;
            if (!suppression && EmployeeRuntimeManager.isWorkTargetReserved(abnormality, employee.getUUID())) {
                eligible = false;
                reason = "异想体已有工作安排";
            } else if (EmployeeRuntimeManager.hasLockedMalkuthWorkOrder(employee.getUUID())) {
                eligible = false;
                reason = "Malkuth III 阶段后无法取消既有工作";
            } else if (suppression && !abnormality.hasEscape()) {
                eligible = false;
                reason = "异想体尚未出逃";
            } else if (employee.level() != abnormality.level()) {
                eligible = false;
                reason = "不在目标设施";
            } else if (panicking) {
                eligible = false;
                reason = "精神崩溃";
            } else if (EntitySnowQueen.isFrozen(employee)) {
                eligible = false;
                reason = "被冰雪女皇冰封";
            } else if (EntityRedShoes.isRedShoesControlled(employee)) {
                eligible = false;
                reason = "被红舞鞋控制";
            } else if (!suppression && stats[4] < abnormality.getRequiredEmployeeLevel()) {
                eligible = false;
                reason = "员工等级不足";
            } else if (WorkManager.isPlayerWorking(employee)) {
                eligible = false;
                reason = "正在工作";
            } else if (suppression && record.taskType() == EmployeeTaskType.SEARCH_AND_SUPPRESS) {
                eligible = false;
                reason = "正在执行镇压";
            } else if (suppression && !EgoLoadoutService.hasValidAssignedAiWeapon(employee,
                    AiEgoRepositoryData.get(employee.server))) {
                eligible = false;
                reason = "未分配可用的 E.G.O 武器";
            }

            if (!suppression && workInfoUnlocked) {
                WorkManager.WorkSession preview = new WorkManager.WorkSession(
                        employee, abnormality, workType, false);
                successRate = preview.actualSuccessRate
                        - FacilityManager.repeatedWorkPenalty(employee.serverLevel(), abnormality);
                int outputs = Math.max(1, abnormality.getMaxPEOutput());
                estimatedSeconds = Math.max(1,
                        (int) Math.ceil(outputs * preview.extractionInterval / 20.0D));
            }
        }

        WorkManager.WorkSession activeWork = WorkManager.getWorkSession(employee);
        ResourceLocation workRoomDimension = activeWork == null
                ? null : activeWork.abnormality.getHomeRoomDimension();
        net.minecraft.core.BlockPos workRoomPosition = activeWork == null
                ? null : activeWork.abnormality.getHomeRoomMarker();
        int workSuccessBoxes = activeWork == null ? 0 : activeWork.successCount;
        int workFailureBoxes = activeWork == null ? 0 : activeWork.failureCount;
        int workBoxCapacity = activeWork == null ? 0 : Math.max(0, activeWork.abnormality.getMaxPEOutput());
        // The monitor reports what is actually being performed. During Malkuth's
        // suppression this can differ from the command slot the dispatcher selected.
        WorkType currentWorkType = activeWork == null ? null : activeWork.workType;

        ResourceLocation dimension = employee.serverLevel().dimension().location();
        String status = isControlled(employee) ? "CONTROLLED" : record.taskType().name();
        return new RoomEmployeeSnapshot(record.id(), record.name(), record.department().id(),
                dimension, employee.blockPosition(), employee.getHealth(), employee.getMaxHealth(),
                mental[0], mental[1], stats[0], stats[1], stats[2], stats[3], stats[4],
                itemName(employee.getItemBySlot(EquipmentSlot.MAINHAND)),
                itemName(employee.getItemBySlot(EquipmentSlot.CHEST)),
                status, commandable, eligible, reason, successRate, estimatedSeconds,
                workRoomDimension, workRoomPosition,
                currentWorkType, workSuccessBoxes, workFailureBoxes, workBoxCapacity);
    }

    private static RoomEmployeeSnapshot playerSnapshot(ServerPlayer player) {
        float[] mental = {0.0F, 0.0F};
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(value -> {
            mental[0] = value.getMentalValue();
            mental[1] = value.getEffectiveMaxMentalValue();
        });
        int[] stats = {1, 1, 1, 1, 1};
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(value -> {
            stats[0] = value.getFortitude();
            stats[1] = value.getPrudence();
            stats[2] = value.getTemperance();
            stats[3] = value.getJustice();
            stats[4] = value.getEmployeeLevel();
        });
        ResourceLocation dimension = player.serverLevel().dimension().location();
        String department = monitoredPlayerDepartment(
                PlayerFacilityProfileData.get(player.server), player.getUUID());
        return new RoomEmployeeSnapshot(player.getUUID(), player.getGameProfile().getName(), department,
                dimension, player.blockPosition(), player.getHealth(), player.getMaxHealth(),
                mental[0], mental[1], stats[0], stats[1], stats[2], stats[3], stats[4],
                itemName(player.getItemBySlot(EquipmentSlot.MAINHAND)),
                itemName(player.getItemBySlot(EquipmentSlot.CHEST)),
                isControlled(player) ? "CONTROLLED" : "PLAYER",
                false, false, "真人玩家不可调度", -1.0F, 0,
                null, null, null, 0, 0, 0);
    }

    private static boolean isControlled(ServerPlayer player) {
        return EntitySnowQueen.isFrozen(player) || EntityRedShoes.isRedShoesControlled(player);
    }

    static String monitoredPlayerDepartment(PlayerFacilityProfileData profiles, UUID playerId) {
        if (profiles == null || playerId == null) return EmployeeDepartment.UNASSIGNED.id();
        return profiles.department(playerId).id();
    }

    private static String itemName(ItemStack stack) {
        return stack.isEmpty() ? "无" : stack.getHoverName().getString();
    }

}
