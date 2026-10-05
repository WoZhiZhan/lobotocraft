package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahGroundImpactEffects;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/** One event per E.G.O ground impact, not one packet per shard or victim. */
public final class GeburahGroundImpactPacket implements IMessage {
    private ResourceLocation dimension;
    private Vec3 impact;
    private float radius;
    private int seed;

    public GeburahGroundImpactPacket() {
    }

    public GeburahGroundImpactPacket(ResourceLocation dimension, Vec3 impact,
                                    float radius, int seed) {
        this.dimension = dimension;
        this.impact = impact;
        this.radius = radius;
        this.seed = seed;
        validate();
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        validate();
        buf.writeResourceLocation(dimension);
        buf.writeDouble(impact.x);
        buf.writeDouble(impact.y);
        buf.writeDouble(impact.z);
        buf.writeFloat(radius);
        buf.writeInt(seed);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        impact = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        radius = buf.readFloat();
        seed = buf.readInt();
        validate();
    }

    private void validate() {
        if (dimension == null || impact == null || !Double.isFinite(impact.x)
                || !Double.isFinite(impact.y) || !Double.isFinite(impact.z)
                || Math.abs(impact.x) > 30_000_000.0D || Math.abs(impact.z) > 30_000_000.0D
                || Math.abs(impact.y) > 30_000_000.0D
                || !Float.isFinite(radius) || radius <= 0.0F || radius > 3.0F) {
            throw new IllegalArgumentException("Invalid Geburah ground impact");
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        GeburahGroundImpactEffects.spawn(dimension, impact, radius, seed);
    }
}
