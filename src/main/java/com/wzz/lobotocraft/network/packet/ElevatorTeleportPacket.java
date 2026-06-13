package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.ClientPacketHandler;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public class ElevatorTeleportPacket implements IMessage {
    private Vec3 startPos;
    private Vec3 endPos;
    private int duration; // 动画持续时间(ticks)

    public ElevatorTeleportPacket(Vec3 startPos, Vec3 endPos, int duration) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.duration = duration;
    }

    public Vec3 getStartPos() { return startPos; }
    public Vec3 getEndPos() { return endPos; }
    public int getDuration() { return duration; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.duration = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(startPos.x);
        buf.writeDouble(startPos.y);
        buf.writeDouble(startPos.z);
        buf.writeDouble(endPos.x);
        buf.writeDouble(endPos.y);
        buf.writeDouble(endPos.z);
        buf.writeInt(duration);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ClientPacketHandler.handleElevatorTeleport(this);
        });
        ctx.setPacketHandled(true);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}