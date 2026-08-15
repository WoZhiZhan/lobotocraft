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
        if (!RequestThrottle.allow(sender, "room_work_assignment_" + employeeId, 150L)) return;
        String failure = validateAndAssign(sender);
        boolean success = failure == null;
        MessageLoader.getLoader().sendToPlayer(sender,
                new RoomWorkAssignmentResultPacket(employeeId, success,
                        success ? "message.lobotocraft.monitor.dispatch.work_sent" : failure));
    }

    private String validateAndAssign(ServerPlayer sender) {
        EmployeeRecord record = FacilityPersonnelData.get(sender.server).get(employeeId);
        if (record == null || record.retired()) return "message.lobotocraft.monitor.dispatch.employee_missing";
        if (!com.wzz.lobotocraft.employee.DepartmentProgressService.isSectionOpen(
                sender.server, record.department(), record.centralSection())) {
            return "message.lobotocraft.monitor.dispatch.department_closed";
        }
        if (!LlmDepartmentAccessData.get(sender.server).canManage(sender, record.department())) {
            return "message.lobotocraft.monitor.dispatch.unauthorized";
        }
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(employeeId);
        if (employee == null || !employee.isAlive() || employee.isRemoved()) {
            return "message.lobotocraft.monitor.dispatch.employee_not_deployed";
        }

        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return "message.lobotocraft.monitor.dispatch.room_invalid";
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                sender.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            return access == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "message.lobotocraft.monitor.dispatch.room_unexpanded"
                    : "message.lobotocraft.monitor.dispatch.room_unavailable";
        }
        if (!(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            return "message.lobotocraft.monitor.dispatch.room_invalid";
        }
        Entity entity = marker.getBoundAbnormality() == null
                ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
            return "message.lobotocraft.monitor.dispatch.abnormality_unavailable";
        }
        if (abnormality.isToolType()) {
            return EmployeeRuntimeManager.assignToolUse(record, abnormality)
                    ? null : "message.lobotocraft.monitor.dispatch.tool_failed";
        }
        if (employee.level() != abnormality.level()) {
            return "message.lobotocraft.monitor.dispatch.wrong_facility";
        }
        if (EmployeeRuntimeManager.isWorkTargetReserved(abnormality, employeeId)) {
            return "message.lobotocraft.monitor.dispatch.work_reserved";
        }
        if (WorkManager.isPlayerWorking(employee)) {
            return "message.lobotocraft.monitor.dispatch.employee_working";
        }

        boolean mentalBroken = MentalValueUtil.isPanic(employee);
        if (mentalBroken) return "message.lobotocraft.monitor.dispatch.employee_panicking";
        int employeeLevel = employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(value -> value.getEmployeeLevel()).orElse(1);
        if (employeeLevel < abnormality.getRequiredEmployeeLevel()) {
            return "message.lobotocraft.monitor.dispatch.employee_level";
        }
        return EmployeeRuntimeManager.assignMonitorWork(record, abnormality, workType)
                ? null : "message.lobotocraft.monitor.dispatch.work_failed";
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
