package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class UpdateDepartmentAccessPacket implements IMessage {
    private static final int MAX_UPDATES = 128;
    private static final int MAX_DEPARTMENTS = 10;
    private List<LlmDepartmentAccessService.Update> updates = List.of();

    public UpdateDepartmentAccessPacket() {
    }

    public UpdateDepartmentAccessPacket(List<LlmDepartmentAccessService.Update> updates) {
        this.updates = List.copyOf(updates);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_UPDATES) throw new IllegalArgumentException("Invalid access update count");
        ArrayList<LlmDepartmentAccessService.Update> decoded = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID playerId = buffer.readUUID();
            int departmentCount = buffer.readVarInt();
            if (departmentCount < 0 || departmentCount > MAX_DEPARTMENTS) {
                throw new IllegalArgumentException("Invalid department count");
            }
            EnumSet<EmployeeDepartment> departments = EnumSet.noneOf(EmployeeDepartment.class);
            for (int departmentIndex = 0; departmentIndex < departmentCount; departmentIndex++) {
                EmployeeDepartment department = EmployeeDepartment.byId(buffer.readUtf(32));
                if (department == EmployeeDepartment.UNASSIGNED) {
                    throw new IllegalArgumentException("Invalid department");
                }
                departments.add(department);
            }
            decoded.add(new LlmDepartmentAccessService.Update(playerId, departments));
        }
        updates = List.copyOf(decoded);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        int updateCount = Math.min(updates.size(), MAX_UPDATES);
        buffer.writeVarInt(updateCount);
        for (int updateIndex = 0; updateIndex < updateCount; updateIndex++) {
            LlmDepartmentAccessService.Update update = updates.get(updateIndex);
            buffer.writeUUID(update.playerId());
            int departmentCount = Math.min(update.departments().size(), MAX_DEPARTMENTS);
            buffer.writeVarInt(departmentCount);
            int written = 0;
            for (EmployeeDepartment department : update.departments()) {
                if (written++ >= departmentCount) break;
                buffer.writeUtf(department.id(), 32);
            }
        }
    }

    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer operator = context.getSender();
        if (operator == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(operator.level())) return;
        if (!LlmDepartmentAccessService.apply(operator, updates)) {
            operator.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.department_access.save_failed"));
            return;
        }
        operator.sendSystemMessage(Component.translatable(
                "message.lobotocraft.department_access.saved"));
        LlmDepartmentAccessService.refresh(operator);
    }
}
