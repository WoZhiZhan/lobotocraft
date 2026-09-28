package com.wzz.lobotocraft.debug.prototype.queenlaser;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class QueenLaserPrototypePacket implements IMessage {
    static final int ACTION_CLEAR = 0;
    static final int ACTION_START = 1;
    public static final int MODE_PREVIEW = 0;
    public static final int MODE_MAX = 1;

    private int action;
    private int mode;
    private double startX;
    private double startY;
    private double startZ;
    private double endX;
    private double endY;
    private double endZ;
    private int castTicks;
    private int durationTicks;
    private int ageTicks;

    public QueenLaserPrototypePacket() {
    }

    private QueenLaserPrototypePacket(int action, Vec3 start, Vec3 end, int mode,
                                      int castTicks, int durationTicks, int ageTicks) {
        this.action = action;
        this.mode = mode;
        this.startX = start.x;
        this.startY = start.y;
        this.startZ = start.z;
        this.endX = end.x;
        this.endY = end.y;
        this.endZ = end.z;
        this.castTicks = castTicks;
        this.durationTicks = durationTicks;
        this.ageTicks = ageTicks;
    }

    public static QueenLaserPrototypePacket start(Vec3 start, Vec3 end, int mode, int durationTicks) {
        return start(start, end, mode, QueenLaserTimeline.CAST_TICKS, durationTicks);
    }

    public static QueenLaserPrototypePacket start(Vec3 start, Vec3 end, int mode,
                                                   int castTicks, int durationTicks) {
        return new QueenLaserPrototypePacket(ACTION_START, start, end, mode,
                castTicks, durationTicks, 0);
    }

    public static QueenLaserPrototypePacket resume(Vec3 start, Vec3 end, int mode,
                                                    int castTicks, int durationTicks,
                                                    int ageTicks) {
        return new QueenLaserPrototypePacket(ACTION_START, start, end, mode,
                castTicks, durationTicks, ageTicks);
    }

    public static QueenLaserPrototypePacket clear() {
        return new QueenLaserPrototypePacket(ACTION_CLEAR, Vec3.ZERO, Vec3.ZERO,
                MODE_PREVIEW, 0, 0, 0);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeByte(mode);
        buf.writeDouble(startX);
        buf.writeDouble(startY);
        buf.writeDouble(startZ);
        buf.writeDouble(endX);
        buf.writeDouble(endY);
        buf.writeDouble(endZ);
        buf.writeVarInt(castTicks);
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(ageTicks);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        action = buf.readUnsignedByte();
        mode = buf.readUnsignedByte();
        startX = buf.readDouble();
        startY = buf.readDouble();
        startZ = buf.readDouble();
        endX = buf.readDouble();
        endY = buf.readDouble();
        endZ = buf.readDouble();
        castTicks = buf.readVarInt();
        durationTicks = buf.readVarInt();
        ageTicks = buf.readVarInt();
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        if (action == ACTION_CLEAR) {
            QueenLaserPrototypeManager.clear();
            return;
        }
        QueenLaserPrototypeManager.start(
                new Vec3(startX, startY, startZ),
                new Vec3(endX, endY, endZ),
                mode,
                castTicks,
                durationTicks,
                ageTicks);
    }
}
