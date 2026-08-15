package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.PlayerStatusClientState;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record PlayerStatusPacket(List<Entry> entries) {
    private static final int MAX_PLAYERS = 1024;
    public static void encode(PlayerStatusPacket packet, FriendlyByteBuf buffer) {
        int entryCount = Math.min(packet.entries.size(), MAX_PLAYERS);
        buffer.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeUUID(entry.playerId);
            buffer.writeVarInt(entry.state);
            buffer.writeComponent(entry.workTarget);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeFloat(entry.mental);
            buffer.writeFloat(entry.maxMental);
        }
    }

    public static PlayerStatusPacket decode(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readVarIntCount(buffer, MAX_PLAYERS, "player status");
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readVarInt(),
                buffer.readComponent(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat()));
        }
        return new PlayerStatusPacket(entries);
    }

    public static void handle(PlayerStatusPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> PlayerStatusClientState.update(packet.entries)));
        context.setPacketHandled(true);
    }

    public record Entry(UUID playerId, int state, Component workTarget,
                        float health, float maxHealth, float mental, float maxMental) {
    }
}
