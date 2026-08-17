package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahEffectCompositor;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class GeburahVisualEffectPacket implements IMessage {
    private int typeOrdinal;
    private double originX;
    private double originY;
    private double originZ;
    private float directionX;
    private float directionZ;
    private float intensity;
    private float hitSeverity;
    private boolean ego;
    private int durationTicks;
    private int seed;

    public GeburahVisualEffectPacket() {
    }

    public GeburahVisualEffectPacket(GeburahVisualEffectType type, Vec3 origin,
                                     Vec3 direction, float intensity,
                                     float hitSeverity,
                                     int durationTicks, int seed) {
        this(type, origin, direction, intensity, hitSeverity, false, durationTicks, seed);
    }

    public GeburahVisualEffectPacket(GeburahVisualEffectType type, Vec3 origin,
                                     Vec3 direction, float intensity,
                                     float hitSeverity, boolean ego,
                                     int durationTicks, int seed) {
        this.typeOrdinal = type.ordinal();
        this.originX = origin.x;
        this.originY = origin.y;
        this.originZ = origin.z;
        this.directionX = (float) direction.x;
        this.directionZ = (float) direction.z;
        this.intensity = intensity;
        this.hitSeverity = hitSeverity;
        this.ego = ego;
        this.durationTicks = durationTicks;
        this.seed = seed;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        typeOrdinal = buf.readVarInt();
        originX = buf.readDouble();
        originY = buf.readDouble();
        originZ = buf.readDouble();
        directionX = buf.readFloat();
        directionZ = buf.readFloat();
        intensity = buf.readFloat();
        hitSeverity = buf.readFloat();
        ego = buf.readBoolean();
        durationTicks = buf.readVarInt();
        seed = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(typeOrdinal);
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeFloat(directionX);
        buf.writeFloat(directionZ);
        buf.writeFloat(intensity);
        buf.writeFloat(hitSeverity);
        buf.writeBoolean(ego);
        buf.writeVarInt(durationTicks);
        buf.writeInt(seed);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        GeburahVisualEffectType type = GeburahVisualEffectType.byOrdinal(typeOrdinal);
        if (type == null) return;
        GeburahEffectCompositor.trigger(type, new Vec3(originX, originY, originZ),
                new Vec3(directionX, 0.0D, directionZ), intensity, hitSeverity,
                ego, durationTicks, seed);
    }
}
