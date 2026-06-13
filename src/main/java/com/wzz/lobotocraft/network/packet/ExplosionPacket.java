package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.renderer.ExplosionRenderer;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public class ExplosionPacket implements IMessage {
    private double x, y, z;
    private float time;
    private float radius;

    public ExplosionPacket(double x, double y, double z, float time, float outerRadius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;
        this.radius = outerRadius;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.time = buf.readFloat();
        this.radius = buf.readFloat();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(time);
        buf.writeFloat(radius);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 pos = new Vec3(x, y, z);
                ExplosionRenderer.createExplosion(pos, time, radius);
            }
        });
        ctx.setPacketHandled(true);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}