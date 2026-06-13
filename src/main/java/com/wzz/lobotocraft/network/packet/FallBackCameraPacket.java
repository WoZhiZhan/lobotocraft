package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.event.FallBackCameraEffect;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class FallBackCameraPacket implements IMessage {

    public FallBackCameraPacket() {
    }

    @Override
    public boolean sendToClient() { return true; }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }
    
    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                FallBackCameraEffect.triggerFallBack(Minecraft.getInstance().player);
            }
        });
        ctx.setPacketHandled(true);
    }
}