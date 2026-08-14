package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.compat.ysm.YsmEmployeeCompat;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/** Server-authoritative appearance update for an already recruited employee. */
public final class SetEmployeeAppearancePacket implements IMessage {
    private UUID employeeId;
    private EmployeeAppearance appearance = EmployeeAppearance.DEFAULT;

    public SetEmployeeAppearancePacket() {
    }

    public SetEmployeeAppearancePacket(UUID employeeId, EmployeeAppearance appearance) {
        this.employeeId = employeeId;
        this.appearance = appearance == null
                ? EmployeeAppearance.DEFAULT : appearance.sanitized();
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        employeeId = buffer.readUUID();
        appearance = new EmployeeAppearance(
                buffer.readUtf(EmployeeAppearance.MAX_PROVIDER_LENGTH),
                buffer.readUtf(EmployeeAppearance.MAX_MODEL_ID_LENGTH),
                buffer.readUtf(EmployeeAppearance.MAX_MODEL_PATH_LENGTH),
                buffer.readUtf(EmployeeAppearance.MAX_TEXTURE_ID_LENGTH)).sanitized();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(employeeId);
        EmployeeAppearance clean = appearance == null
                ? EmployeeAppearance.DEFAULT : appearance.sanitized();
        buffer.writeUtf(clean.provider(), EmployeeAppearance.MAX_PROVIDER_LENGTH);
        buffer.writeUtf(clean.modelId(), EmployeeAppearance.MAX_MODEL_ID_LENGTH);
        buffer.writeUtf(clean.modelPath(), EmployeeAppearance.MAX_MODEL_PATH_LENGTH);
        buffer.writeUtf(clean.textureId(), EmployeeAppearance.MAX_TEXTURE_ID_LENGTH);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || employeeId == null || !sender.hasPermissions(2)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;

        EmployeeRecord record = FacilityPersonnelData.get(sender.server).get(employeeId);
        if (record == null || record.retired()) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_appearance.missing"));
            FacilityManagementService.open(sender);
            return;
        }
        var validated = YsmEmployeeCompat.validateRequested(appearance);
        if (validated.isEmpty()) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_appearance.ysm_unavailable"));
            FacilityManagementService.open(sender);
            return;
        }

        EmployeeRuntimeManager.AppearanceChangeResult result =
                EmployeeRuntimeManager.changeAppearance(sender.server, record, validated.get());
        if (result == EmployeeRuntimeManager.AppearanceChangeResult.BUSY) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_appearance.busy"));
        } else if (result == EmployeeRuntimeManager.AppearanceChangeResult.FAILED) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_appearance.failed"));
        } else {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_appearance.updated", record.name()));
        }
        FacilityManagementService.open(sender);
    }
}
