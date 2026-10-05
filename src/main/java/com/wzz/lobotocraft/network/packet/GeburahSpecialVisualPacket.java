package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahSpecialEffects;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals.*;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.network.NetworkEvent;

public final class GeburahSpecialVisualPacket implements IMessage {
    private ResourceLocation dimension;
    private Snapshot snapshot;
    public GeburahSpecialVisualPacket() {}
    public GeburahSpecialVisualPacket(ResourceLocation dimension, Snapshot snapshot) {
        if (dimension == null || snapshot == null) throw new IllegalArgumentException("Missing special visual data");
        this.dimension = dimension;
        this.snapshot = snapshot;
    }
    @Override public boolean sendToClient() { return true; }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeUUID(snapshot.caster());
        buf.writeVarInt(snapshot.entityId());
        buf.writeVarLong(snapshot.castId());
        buf.writeVarInt(snapshot.skill().ordinal());
        buf.writeVarInt(snapshot.event().ordinal());
        buf.writeVarInt(snapshot.elapsed());
        buf.writeInt(snapshot.cueIndex());
        buf.writeBoolean(snapshot.connected());
        writeVec(buf, snapshot.origin()); writeVec(buf, snapshot.target()); writeVec(buf, snapshot.forward());
    }
    @Override public void fromBytes(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        var caster = buf.readUUID();
        int entity = buf.readVarInt();
        long cast = buf.readVarLong();
        int skill = buf.readVarInt(), event = buf.readVarInt();
        if (skill < 0 || skill >= Skill.values().length || event < 0 || event >= Event.values().length)
            throw new IllegalArgumentException("Invalid special visual enum");
        int elapsed = buf.readVarInt(), cue = buf.readInt();
        boolean connected = buf.readBoolean();
        snapshot = new Snapshot(caster, entity, cast, Skill.values()[skill], Event.values()[event],
                elapsed, cue, connected, readVec(buf), readVec(buf), readVec(buf));
    }
    private static void writeVec(FriendlyByteBuf buf, Vec3 p) {
        buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
    }
    private static Vec3 readVec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
    @OnlyIn(Dist.CLIENT)
    @Override public void runClient(NetworkEvent.Context ctx) { GeburahSpecialEffects.accept(dimension, snapshot); }
}
