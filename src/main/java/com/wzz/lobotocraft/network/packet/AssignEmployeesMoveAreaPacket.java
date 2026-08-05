package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityAreaMoveService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Selected monitor employees move to independently sampled positions inside one mapped area. */
public final class AssignEmployeesMoveAreaPacket implements IMessage {

    private static final int MAX_GROUP_LENGTH = 128;

    private String groupId = "";
    private List<UUID> employeeIds = List.of();

    public AssignEmployeesMoveAreaPacket() {
    }

    public AssignEmployeesMoveAreaPacket(String groupId, Iterable<UUID> employeeIds) {
        this.groupId = groupId == null ? "" : groupId;
        if (this.groupId.length() > MAX_GROUP_LENGTH) {
            throw new IllegalArgumentException("Area id is too long");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        if (employeeIds != null) employeeIds.forEach(unique::add);
        if (unique.size() > FacilityAreaMoveService.MAX_EMPLOYEES) {
            throw new IllegalArgumentException("Too many employees in area move command");
        }
        this.employeeIds = List.copyOf(unique);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        groupId = buffer.readUtf(MAX_GROUP_LENGTH);
        int count = buffer.readVarInt();
        if (count < 0 || count > FacilityAreaMoveService.MAX_EMPLOYEES) {
            throw new IllegalArgumentException("Invalid area move employee count: " + count);
        }
        List<UUID> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) decoded.add(buffer.readUUID());
        employeeIds = List.copyOf(decoded);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(groupId, MAX_GROUP_LENGTH);
        buffer.writeVarInt(employeeIds.size());
        for (UUID employeeId : employeeIds) buffer.writeUUID(employeeId);
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        if (!RequestThrottle.allow(sender, "monitor_area_move", 350L)) return;
        FacilityAreaMoveService.Result result =
                FacilityAreaMoveService.dispatch(sender, groupId, employeeIds);
        String color = result.successful() ? "§b" : "§c";
        sender.displayClientMessage(Component.literal(color + "[对讲机] §f" + result.message()), true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
