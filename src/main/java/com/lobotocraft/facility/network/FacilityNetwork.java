package com.lobotocraft.facility.network;

import com.lobotocraft.facility.FacilityPatch;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FacilityNetwork {
    // Bump whenever a packet id or codec in this channel changes.
    private static final String PROTOCOL = "5";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(FacilityPatch.MOD_ID, "facility"))
        .networkProtocolVersion(() -> PROTOCOL)
        .clientAcceptedVersions(PROTOCOL::equals)
        .serverAcceptedVersions(PROTOCOL::equals)
        .simpleChannel();

    private FacilityNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, FacilityStatePacket.class,
            FacilityStatePacket::encode, FacilityStatePacket::decode,
            FacilityStatePacket::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, CarmenConversationPacket.class,
            CarmenConversationPacket::encode, CarmenConversationPacket::decode,
            CarmenConversationPacket::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, PlayerStatusPacket.class,
            PlayerStatusPacket::encode, PlayerStatusPacket::decode,
            PlayerStatusPacket::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, ReactorStatusPacket.class,
            ReactorStatusPacket::encode, ReactorStatusPacket::decode,
            ReactorStatusPacket::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, DayVoteActionPacket.class,
            DayVoteActionPacket::encode, DayVoteActionPacket::decode,
            DayVoteActionPacket::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer player, FacilityStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void send(ServerPlayer player, CarmenConversationPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void send(ServerPlayer player, PlayerStatusPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void send(ServerPlayer player, ReactorStatusPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(DayVoteActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
