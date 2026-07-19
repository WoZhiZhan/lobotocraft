package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.event.listener.FallBackCameraEffect;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (Minecraft.getInstance().player != null) {
            FallBackCameraEffect.triggerFallBack(Minecraft.getInstance().player);
        }
    }
}