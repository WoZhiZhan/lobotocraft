package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Protocol tombstone for the retired remote-work flow. */
public final class StartRoomWorkPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;

    public StartRoomWorkPacket() {
    }

    public StartRoomWorkPacket(ResourceLocation dimension, BlockPos roomPos) {
        this.dimension = dimension;
        this.roomPos = roomPos;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        roomPos = buf.readBlockPos();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(roomPos);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        // Remote work is intentionally unsupported; normal entity interaction owns validation.
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
