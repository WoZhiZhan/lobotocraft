package com.wzz.lobotocraft.debug.prototype.queenstar;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class QueenStarPrototypePacket implements IMessage {
    static final int ACTION_CLEAR = 0;
    static final int ACTION_START = 1;
    private int action;
    private double startX;
    private double startY;
    private double startZ;
    private double endX;
    private double endY;
    private double endZ;
    private int travelTicks;

    public QueenStarPrototypePacket() {
    }

    private QueenStarPrototypePacket(int action, Vec3 start, Vec3 end, int travelTicks) {
        this.action = action;
        this.startX = start.x;
        this.startY = start.y;
        this.startZ = start.z;
        this.endX = end.x;
        this.endY = end.y;
        this.endZ = end.z;
        this.travelTicks = travelTicks;
    }

    public static QueenStarPrototypePacket start(Vec3 start, Vec3 end, int travelTicks) {
        return new QueenStarPrototypePacket(ACTION_START, start, end, travelTicks);
    }

    public static QueenStarPrototypePacket clear() {
        return new QueenStarPrototypePacket(ACTION_CLEAR, Vec3.ZERO, Vec3.ZERO, 0);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeDouble(startX);
        buf.writeDouble(startY);
        buf.writeDouble(startZ);
        buf.writeDouble(endX);
        buf.writeDouble(endY);
        buf.writeDouble(endZ);
        buf.writeVarInt(travelTicks);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        action = buf.readUnsignedByte();
        startX = buf.readDouble();
        startY = buf.readDouble();
        startZ = buf.readDouble();
        endX = buf.readDouble();
        endY = buf.readDouble();
        endZ = buf.readDouble();
        travelTicks = buf.readVarInt();
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        if (action == ACTION_CLEAR) {
            QueenStarPrototypeManager.clear();
            return;
        }
        QueenStarPrototypeManager.start(new Vec3(startX, startY, startZ),
                new Vec3(endX, endY, endZ), travelTicks);
    }
}
