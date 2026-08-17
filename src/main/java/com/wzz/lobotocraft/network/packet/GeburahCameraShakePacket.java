package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahCameraShakeState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class GeburahCameraShakePacket implements IMessage {
    private int durationTicks;
    private float amplitude;
    private int seed;

    public GeburahCameraShakePacket() {
    }

    public GeburahCameraShakePacket(int durationTicks, float amplitude, int seed) {
        this.durationTicks = durationTicks;
        this.amplitude = amplitude;
        this.seed = seed;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        durationTicks = buf.readVarInt();
        amplitude = buf.readFloat();
        seed = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(durationTicks);
        buf.writeFloat(amplitude);
        buf.writeInt(seed);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        GeburahCameraShakeState.trigger(durationTicks, amplitude, seed);
    }
}
