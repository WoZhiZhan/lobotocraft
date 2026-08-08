package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.PlayerFacilityProfileData;
import com.wzz.lobotocraft.employee.PlayerProfileService;
import com.wzz.lobotocraft.event.listener.PlayerTabListDayHandler;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class SetPlayerDepartmentPacket implements IMessage {
    private String departmentId;

    public SetPlayerDepartmentPacket() {
    }

    public SetPlayerDepartmentPacket(String departmentId) {
        this.departmentId = departmentId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        departmentId = buffer.readUtf(32);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(departmentId, 32);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())) return;
        EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
        boolean open = DepartmentProgressData.get(player.server).isOpen(department);
        if (!department.id().equals(departmentId)
                || !PlayerFacilityProfileData.canSelect(department, open)) {
            player.sendSystemMessage(Component.literal("§c该部门尚未开放，无法加入。"));
            PlayerProfileService.open(player, PlayerProfileService.View.DEPARTMENT);
            return;
        }

        PlayerFacilityProfileData.get(player.server).setDepartment(player.getUUID(), department);
        player.refreshDisplayName();
        PlayerTabListDayHandler.syncDepartmentToAll(player);
        player.sendSystemMessage(Component.literal("§a已加入" + department.displayName()
                + "。此身份不占用员工槽位。"));
        PlayerProfileService.open(player, PlayerProfileService.View.DEPARTMENT);
    }
}
