package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahStatusClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class GeburahStatusSyncPacket implements IMessage {
    private int bleedStacks;
    private int bleedTicks;

    public GeburahStatusSyncPacket() {
    }

    public GeburahStatusSyncPacket(int bleedStacks, int bleedTicks) {
        this.bleedStacks = bleedStacks;
        this.bleedTicks = bleedTicks;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        bleedStacks = buf.readVarInt();
        bleedTicks = buf.readVarInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(bleedStacks);
        buf.writeVarInt(bleedTicks);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        GeburahStatusClientState.updateBleed(bleedStacks, bleedTicks);
    }
}
