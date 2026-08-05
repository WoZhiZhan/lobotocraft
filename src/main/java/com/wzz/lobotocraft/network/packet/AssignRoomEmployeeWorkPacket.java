package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class AssignRoomEmployeeWorkPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;
    private UUID employeeId;
    private WorkType workType;

    public AssignRoomEmployeeWorkPacket() {
    }

    public AssignRoomEmployeeWorkPacket(ResourceLocation dimension, BlockPos roomPos,
                                        UUID employeeId, WorkType workType) {
        this.dimension = dimension;
        this.roomPos = roomPos;
        this.employeeId = employeeId;
        this.workType = workType;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        dimension = buffer.readResourceLocation();
        roomPos = buffer.readBlockPos();
        employeeId = buffer.readUUID();
        workType = buffer.readEnum(WorkType.class);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(roomPos);
        buffer.writeUUID(employeeId);
        buffer.writeEnum(workType);
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        if (!RequestThrottle.allow(sender, "room_work_assignment", 150L)) return;
        String failure = validateAndAssign(sender);
        boolean success = failure == null;
        MessageLoader.getLoader().sendToPlayer(sender,
                new RoomWorkAssignmentResultPacket(employeeId, success,
                        success ? "任务已发送" : failure));
    }

    private String validateAndAssign(ServerPlayer sender) {
        EmployeeRecord record = FacilityPersonnelData.get(sender.server).get(employeeId);
        if (record == null || record.retired()) return "员工不存在";
        if (!com.wzz.lobotocraft.employee.DepartmentProgressService.isSectionOpen(
                sender.server, record.department(), record.centralSection())) {
            return "该员工所属部门区域尚未开放";
        }
        if (!LlmDepartmentAccessData.get(sender.server).canManage(sender, record.department())) {
            return "你无权调度该部门";
        }
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(employeeId);
        if (employee == null || !employee.isAlive() || employee.isRemoved()) return "员工未部署";

        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return "收容单元已失效";
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                sender.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            return access == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "收容单元尚未扩建" : "收容单元当前不可用";
        }
        if (!(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            return "收容单元已失效";
        }
        Entity entity = marker.getBoundAbnormality() == null
                ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
            return "异想体当前不可用";
        }
        if (abnormality.isToolType()) {
            return EmployeeRuntimeManager.assignToolUse(record, abnormality)
                    ? null : "Unable to dispatch employee to tool";
        }
        if (employee.level() != abnormality.level()) return "员工不在目标设施";
        if (EmployeeRuntimeManager.isWorkTargetReserved(abnormality, employeeId)) {
            return "异想体正在接受工作或已有员工前往";
        }
        if (WorkManager.isPlayerWorking(employee)) {
            return "员工正在工作";
        }

        boolean mentalBroken = MentalValueUtil.isPanic(employee);
        if (mentalBroken) return "员工处于精神崩溃状态";
        int employeeLevel = employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(value -> value.getEmployeeLevel()).orElse(1);
        if (employeeLevel < abnormality.getRequiredEmployeeLevel()) return "员工等级不足";
        return EmployeeRuntimeManager.assignMonitorWork(record, abnormality, workType)
                ? null : "无法发送工作任务";
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
