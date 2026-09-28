package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.fear.FearClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes an employee's horror indicator and, for the local player, sensory feedback. */
public final class FearFeedbackPacket implements IMessage {
    private int entityId;
    private int fearLevel;
    private int threatEntityId;

    public FearFeedbackPacket() {
    }

    public FearFeedbackPacket(int entityId, int fearLevel, int threatEntityId) {
        this.entityId = entityId;
        this.fearLevel = fearLevel;
        this.threatEntityId = threatEntityId;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        fearLevel = buf.readUnsignedByte();
        threatEntityId = buf.readVarInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(fearLevel);
        buf.writeVarInt(threatEntityId);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        FearClientState.trigger(entityId, fearLevel, threatEntityId);
    }
}
