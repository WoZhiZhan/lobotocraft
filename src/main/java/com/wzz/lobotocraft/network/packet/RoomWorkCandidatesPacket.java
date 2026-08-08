package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.RoomMapScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class RoomWorkCandidatesPacket implements IMessage {
    private static final int MAX_EMPLOYEES = 512;
    private BlockPos roomPos;
    private WorkType workType;
    private List<RoomEmployeeSnapshot> employees;
    private String error;

    public RoomWorkCandidatesPacket() {
    }

    public RoomWorkCandidatesPacket(BlockPos roomPos, WorkType workType,
                                    List<RoomEmployeeSnapshot> employees, String error) {
        this.roomPos = roomPos;
        this.workType = workType;
        this.employees = employees;
        this.error = error == null ? "" : error;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        roomPos = buffer.readBlockPos();
        workType = buffer.readEnum(WorkType.class);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_EMPLOYEES) {
            throw new IllegalArgumentException("Invalid work candidate count: " + count);
        }
        employees = new ArrayList<>(count);
        for (int index = 0; index < count; index++) employees.add(RoomEmployeeSnapshot.read(buffer));
        error = buffer.readUtf(128);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(roomPos);
        buffer.writeEnum(workType);
        int count = Math.min(employees.size(), MAX_EMPLOYEES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) employees.get(index).write(buffer);
        buffer.writeUtf(error, 128);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        if (Minecraft.getInstance().screen instanceof RoomMapScreen screen) {
            screen.showWorkCandidates(roomPos, workType, employees, error);
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
