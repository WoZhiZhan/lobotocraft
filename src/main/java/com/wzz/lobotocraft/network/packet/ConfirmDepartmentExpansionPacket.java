package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.DepartmentProgressService;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ConfirmDepartmentExpansionPacket implements IMessage {
    private int pendingDay;
    private String departmentId = "unassigned";

    public ConfirmDepartmentExpansionPacket() {
    }

    public ConfirmDepartmentExpansionPacket(int pendingDay, String departmentId) {
        this.pendingDay = pendingDay;
        this.departmentId = departmentId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        pendingDay = buffer.readVarInt();
        departmentId = buffer.readUtf(32);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(pendingDay);
        buffer.writeUtf(departmentId, 32);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        DepartmentProgressService.confirm(sender, pendingDay,
                EmployeeDepartment.byId(departmentId));
    }
}
