package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class StopAllSoundPacket implements IMessage {

    public StopAllSoundPacket() {
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop();
            }
        });
        ctx.setPacketHandled(true);
    }
}