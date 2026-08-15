package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
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

public final class AssignRoomEmployeeSuppressionPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;
    private UUID employeeId;

    public AssignRoomEmployeeSuppressionPacket() { }

    public AssignRoomEmployeeSuppressionPacket(ResourceLocation dimension, BlockPos roomPos, UUID employeeId) {
        this.dimension = dimension;
        this.roomPos = roomPos;
        this.employeeId = employeeId;
    }

    @Override public void fromBytes(FriendlyByteBuf buffer) {
        dimension = buffer.readResourceLocation();
        roomPos = buffer.readBlockPos();
        employeeId = buffer.readUUID();
    }
    @Override public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(roomPos);
        buffer.writeUUID(employeeId);
    }
    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || !RequestThrottle.allow(sender,
                "room_suppression_assignment_" + employeeId, 100L)) return;
        String failure = validateAndAssign(sender);
        MessageLoader.getLoader().sendToPlayer(sender, new RoomSuppressionAssignmentResultPacket(
                employeeId, failure == null, failure == null
                ? "message.lobotocraft.monitor.dispatch.suppression_sent" : failure));
    }

    private String validateAndAssign(ServerPlayer sender) {
        if (!AbnormalitySpawnHelper.isCompany(sender.level())
                || !sender.serverLevel().dimension().location().equals(dimension)) {
            return "message.lobotocraft.monitor.dispatch.company_only";
        }
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
        if (employee == null || employee.isRemoved() || !employee.isAlive()) {
            return "message.lobotocraft.monitor.dispatch.employee_not_deployed";
        }
        if (WorkManager.isPlayerWorking(employee)) {
            return "message.lobotocraft.monitor.dispatch.employee_working";
        }
        if (MentalValueUtil.isPanic(employee)) {
            return "message.lobotocraft.monitor.dispatch.employee_panicking";
        }
        if (!EgoLoadoutService.hasValidAssignedAiWeapon(employee,
                AiEgoRepositoryData.get(sender.server))) {
            return "message.lobotocraft.monitor.dispatch.no_valid_weapon";
        }
        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || employee.level() != level
                || FacilityAccessService.containmentAccess(sender.server, dimension, roomPos)
                != FacilityAccessService.ContainmentAccess.OPEN
                || !(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            return "message.lobotocraft.monitor.dispatch.room_unavailable";
        }
        Entity entity = marker.getBoundAbnormality() == null ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()
                || !abnormality.hasEscape()) {
            return "message.lobotocraft.monitor.dispatch.abnormality_not_escaped";
        }
        return EmployeeRuntimeManager.assignSuppress(record, abnormality)
                ? null : "message.lobotocraft.monitor.dispatch.suppression_failed";
    }
}
