package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.GeburahEgoHitEffects;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/** One bounded, cosmetic contact event per successful E.G.O slash/stab, not per target. */
public final class GeburahEgoContactPacket implements IMessage {
    private ResourceLocation dimension;
    private GeburahMove move;
    private Vec3 forward;
    private List<Vec3> contacts;
    private int seed;

    public GeburahEgoContactPacket() {
    }

    public GeburahEgoContactPacket(ResourceLocation dimension, GeburahMove move, Vec3 forward,
                                   List<Vec3> contacts, int seed) {
        this.dimension = dimension;
        this.move = move;
        this.forward = forward;
        this.contacts = List.copyOf(contacts);
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
        buf.writeVarInt(move.ordinal());
        writeVec(buf, forward);
        buf.writeVarInt(contacts.size());
        for (Vec3 contact : contacts) writeVec(buf, contact);
        buf.writeInt(seed);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        int ordinal = buf.readVarInt();
        if (ordinal < 0 || ordinal >= GeburahMove.values().length) {
            throw new IllegalArgumentException("Invalid Geburah contact move");
        }
        move = GeburahMove.values()[ordinal];
        forward = readVec(buf);
        int count = buf.readVarInt();
        if (count < 1 || count > GeburahEgoAttackVisuals.MAX_CONTACTS) {
            throw new IllegalArgumentException("Invalid Geburah contact count");
        }
        contacts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) contacts.add(readVec(buf));
        seed = buf.readInt();
        validate();
    }

    private void validate() {
        if (dimension == null || !GeburahEgoAttackVisuals.supports(move) || !finite(forward)
                || forward.lengthSqr() < 0.5D || forward.lengthSqr() > 1.5D
                || Math.abs(forward.y) > 0.001D || contacts == null || contacts.isEmpty()
                || contacts.size() > GeburahEgoAttackVisuals.MAX_CONTACTS
                || contacts.stream().anyMatch(p -> !finite(p) || Math.abs(p.x) > 30_000_000D
                        || Math.abs(p.y) > 30_000_000D || Math.abs(p.z) > 30_000_000D)) {
            throw new IllegalArgumentException("Invalid Geburah EGO contacts");
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        GeburahEgoHitEffects.add(dimension, move, forward, contacts, seed);
    }
}
