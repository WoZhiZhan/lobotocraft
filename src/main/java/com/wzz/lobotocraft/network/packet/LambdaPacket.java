package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.LambdaPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class LambdaPacket implements IMessage {
    private String actionId;
    private CompoundTag data;

    public LambdaPacket() {}

    public LambdaPacket(String actionId, CompoundTag data) {
        this.actionId = actionId;
        this.data = data;
    }

    @Override
    public boolean sendToClient() { return true; }

    @Override
    public boolean sendToServer() { return true; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.actionId = buf.readUtf();
        if (buf.readBoolean()) {
            this.data = buf.readNbt();
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(actionId);
        if (data != null) {
            buf.writeBoolean(true);
            buf.writeNbt(data);
        } else {
            buf.writeBoolean(false);
        }
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                LambdaPacketHandler.runClient(actionId, data);
            } else {
                ServerPlayer sender = ctx.getSender();
                LambdaPacketHandler.runServer(actionId, data, sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}