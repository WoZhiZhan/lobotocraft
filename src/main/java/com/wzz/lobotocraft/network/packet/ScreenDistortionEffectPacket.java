package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.ScreenDistortionState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class ScreenDistortionEffectPacket implements IMessage {
    private float intensity;
    private int durationTicks;

    public ScreenDistortionEffectPacket(float intensity, int durationTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
    }

    @Override public boolean sendToClient() { return true; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.intensity = buf.readFloat();
        this.durationTicks = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(intensity);
        buf.writeInt(durationTicks);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        ScreenDistortionState.activate(intensity, durationTicks);
    }
}