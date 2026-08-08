package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.RoomMapScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public final class RoomSuppressionCandidatesPacket implements IMessage {
    private static final int MAX_EMPLOYEES = 512;
    private BlockPos roomPos;
    private List<RoomEmployeeSnapshot> employees;
    private String error;

    public RoomSuppressionCandidatesPacket() { }

    public RoomSuppressionCandidatesPacket(BlockPos roomPos, List<RoomEmployeeSnapshot> employees,
                                           String error) {
        this.roomPos = roomPos;
        this.employees = employees;
        this.error = error == null ? "" : error;
    }

    @Override public void fromBytes(FriendlyByteBuf buffer) {
        roomPos = buffer.readBlockPos();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_EMPLOYEES) {
            throw new IllegalArgumentException("Invalid suppression employee count: " + count);
        }
        employees = new ArrayList<>(count);
        for (int index = 0; index < count; index++) employees.add(RoomEmployeeSnapshot.read(buffer));
        error = buffer.readUtf(128);
    }
    @Override public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(roomPos);
        int count = Math.min(employees.size(), MAX_EMPLOYEES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) employees.get(index).write(buffer);
        buffer.writeUtf(error, 128);
    }
    @Override public boolean sendToClient() { return true; }

    @OnlyIn(Dist.CLIENT)
    @Override public void runClient(NetworkEvent.Context context) {
        if (Minecraft.getInstance().screen instanceof RoomMapScreen screen) {
            screen.showSuppressionCandidates(roomPos, employees, error);
        }
    }
}
