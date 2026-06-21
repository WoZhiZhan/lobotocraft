package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.ShockwaveEffectManager;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class ShockwaveEffectPacket implements IMessage {
    private double x, y, z;
    private float maxRadius;
    private int color; // 0xRRGGBB

    public ShockwaveEffectPacket(double x, double y, double z, float maxRadius, int color) {
        this.x = x; this.y = y; this.z = z;
        this.maxRadius = maxRadius;
        this.color = color;
    }

    @Override public boolean sendToClient() { return true; }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(maxRadius);
        buf.writeInt(color);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        x = buf.readDouble(); y = buf.readDouble(); z = buf.readDouble();
        maxRadius = buf.readFloat();
        color = buf.readInt();
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        ShockwaveEffectManager.addShockwave(x, y, z, maxRadius, color);
    }
}