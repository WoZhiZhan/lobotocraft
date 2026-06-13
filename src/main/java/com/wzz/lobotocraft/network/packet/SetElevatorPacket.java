package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

public class SetElevatorPacket implements IMessage {
    private BlockPos blockPos;
    private int distance;
    private boolean isUp;

    public SetElevatorPacket(BlockPos blockPos, int distance, boolean isUp) {
        this.blockPos = blockPos;
        this.distance = distance;
        this.isUp = isUp;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int distance = buf.readInt();
        boolean isUp = buf.readBoolean();
        this.blockPos = pos;
        this.distance = distance;
        this.isUp = isUp;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeInt(this.distance);
        buf.writeBoolean(this.isUp);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.getSender().serverLevel();
            if (level.getBlockEntity(this.blockPos) instanceof ElevatorBlockEntity be) {
                be.setTeleportDistance(this.distance);
                be.setTeleportUp(this.isUp);
                be.setChanged();
            }
        });
        ctx.setPacketHandled(true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}