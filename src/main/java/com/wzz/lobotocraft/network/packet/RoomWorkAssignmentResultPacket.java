package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.RoomMapScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class RoomWorkAssignmentResultPacket implements IMessage {
    private UUID employeeId;
    private boolean success;
    private String message;

    public RoomWorkAssignmentResultPacket() {
    }

    public RoomWorkAssignmentResultPacket(UUID employeeId, boolean success, String message) {
        this.employeeId = employeeId;
        this.success = success;
        this.message = message == null ? "" : message;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        employeeId = buffer.readUUID();
        success = buffer.readBoolean();
        message = buffer.readUtf(128);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(employeeId);
        buffer.writeBoolean(success);
        buffer.writeUtf(message, 128);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        if (Minecraft.getInstance().screen instanceof RoomMapScreen screen) {
            screen.onWorkAssignmentResult(employeeId, success, message);
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
