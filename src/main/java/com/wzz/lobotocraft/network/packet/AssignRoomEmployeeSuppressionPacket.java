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
        if (sender == null || !RequestThrottle.allow(sender, "room_suppression_assignment", 100L)) return;
        String failure = validateAndAssign(sender);
        MessageLoader.getLoader().sendToPlayer(sender, new RoomSuppressionAssignmentResultPacket(
                employeeId, failure == null, failure == null ? "镇压任务已下达" : failure));
    }

    private String validateAndAssign(ServerPlayer sender) {
        if (!AbnormalitySpawnHelper.isCompany(sender.level())
                || !sender.serverLevel().dimension().location().equals(dimension)) {
            return "请回到公司后再下达镇压任务";
        }
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
        if (employee == null || employee.isRemoved() || !employee.isAlive()) return "员工未部署";
        if (WorkManager.isPlayerWorking(employee)) {
            return "员工正在工作";
        }
        if (MentalValueUtil.isPanic(employee)) return "员工处于精神崩溃状态";
        if (!EgoLoadoutService.hasValidAssignedAiWeapon(employee,
                AiEgoRepositoryData.get(sender.server))) {
            return "员工未分配可用且满足等级要求的 E.G.O 武器";
        }
        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || employee.level() != level
                || FacilityAccessService.containmentAccess(sender.server, dimension, roomPos)
                != FacilityAccessService.ContainmentAccess.OPEN
                || !(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            return "收容单元当前不可用";
        }
        Entity entity = marker.getBoundAbnormality() == null ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()
                || !abnormality.hasEscape()) return "异想体当前未处于出逃状态";
        return EmployeeRuntimeManager.assignSuppress(record, abnormality) ? null : "无法下达镇压任务";
    }
}
