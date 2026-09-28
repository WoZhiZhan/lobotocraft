package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.item.ego.base.IEgoLevelItem;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public final class AssignAiEgoPacket implements IMessage {
    private UUID employeeId;
    private String abnormalityCode;
    private String equipmentType;
    private UUID sourceOwnerId;

    public AssignAiEgoPacket() {
    }

    public AssignAiEgoPacket(UUID employeeId, String abnormalityCode, String equipmentType) {
        this(employeeId, abnormalityCode, equipmentType, null);
    }

    public AssignAiEgoPacket(UUID employeeId, String abnormalityCode, String equipmentType,
                             UUID sourceOwnerId) {
        this.employeeId = employeeId;
        this.abnormalityCode = abnormalityCode;
        this.equipmentType = equipmentType;
        this.sourceOwnerId = sourceOwnerId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        employeeId = buf.readUUID();
        abnormalityCode = buf.readUtf(256);
        equipmentType = buf.readUtf(256);
        sourceOwnerId = buf.readBoolean() ? buf.readUUID() : null;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(employeeId);
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(equipmentType);
        buf.writeBoolean(sourceOwnerId != null);
        if (sourceOwnerId != null) buf.writeUUID(sourceOwnerId);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender == null || !sender.hasPermissions(2)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())
                || (!"weapon".equals(equipmentType) && !"armor".equals(equipmentType))) return;
        if (FacilityPersonnelData.get(sender.server).get(employeeId) == null) return;
        var employee = EmployeeRuntimeManager.activePlayer(employeeId);
        if (employee == null) {
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.employee_offline"));
            return;
        }
        if (MentalValueUtil.isPanic(employee)) {
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.panic_change"));
            return;
        }
        if (isControlled(employee)) {
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.controlled_change"));
            return;
        }
        AiEgoRepositoryData repository = AiEgoRepositoryData.get(sender.server);
        if (abnormalityCode.isBlank()) {
            EgoLoadoutService.releaseAi(employee, repository, equipmentType);
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.unequipped"));
            FacilityManagementService.openEgoAssignment(sender);
            return;
        }
        AiEgoRepositoryData.Stock stock = repository.stock(abnormalityCode, equipmentType);
        if (stock == null) {
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.invalid"));
            return;
        }
        var missing = EgoLoadoutService.missingRequirementComponents(employee, stock.itemIds());
        if (!missing.isEmpty()) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.ai_ego.requirements_missing",
                    IEgoLevelItem.joinMissingLevels(missing)));
            return;
        }
        String previousCode = repository.assignedCode(employeeId, equipmentType);
        ServerPlayer sourceOwner = null;
        if (sourceOwnerId != null && !sourceOwnerId.equals(employeeId)) {
            if (FacilityPersonnelData.get(sender.server).get(sourceOwnerId) == null
                    || !abnormalityCode.equals(repository.assignedCode(sourceOwnerId, equipmentType))) {
                sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.assignment_changed"));
                return;
            }
            sourceOwner = EmployeeRuntimeManager.activePlayer(sourceOwnerId);
            if (sourceOwner != null && MentalValueUtil.isPanic(sourceOwner)) {
                sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.panic_transfer"));
                return;
            }
            if (sourceOwner != null && isControlled(sourceOwner)) {
                sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.controlled_transfer"));
                return;
            }
            repository.release(sourceOwnerId, equipmentType);
        }

        if (!repository.assign(employeeId, abnormalityCode, equipmentType)) {
            if (sourceOwnerId != null) repository.assign(sourceOwnerId, abnormalityCode, equipmentType);
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.stock_invalid"));
            return;
        }

        if (!EgoLoadoutService.equipAi(employee, repository, abnormalityCode, equipmentType)) {
            repository.release(employeeId, equipmentType);
            if (!previousCode.isBlank()) repository.assign(employeeId, previousCode, equipmentType);
            if (sourceOwnerId != null) repository.assign(sourceOwnerId, abnormalityCode, equipmentType);
            if (!previousCode.isBlank()) {
                EgoLoadoutService.equipAi(employee, repository, previousCode, equipmentType);
            }
            sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.invalid"));
            return;
        }
        if (sourceOwner != null) EgoLoadoutService.clearAiProjection(sourceOwner, equipmentType);
        sender.sendSystemMessage(Component.translatable("message.lobotocraft.ai_ego.assigned"));
        FacilityManagementService.openEgoAssignment(sender);
    }

    private static boolean isControlled(ServerPlayer player) {
        return EntitySnowQueen.isFrozen(player) || HostileControlState.isActive(player);
    }
}
