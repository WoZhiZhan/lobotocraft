package com.wzz.lobotocraft.employee;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;

import java.util.Set;

final class EmployeePacketListener extends ServerGamePacketListenerImpl {
    EmployeePacketListener(MinecraftServer server, ServerPlayer player) {
        super(server, new Connection(PacketFlow.CLIENTBOUND), player);
    }

    @Override public void tick() { }
    @Override public void resetPosition() { }
    @Override public void disconnect(Component reason) { }
    @Override public void onDisconnect(Component reason) { }
    @Override public void send(Packet<?> packet) { }
    @Override public void send(Packet<?> packet, PacketSendListener listener) { }

    @Override
    public void teleport(double x, double y, double z, float yaw, float pitch) {
        player.absMoveTo(x, y, z, yaw, pitch);
        player.serverLevel().getChunkSource().move(player);
    }

    @Override
    public void teleport(double x, double y, double z, float yaw, float pitch,
                         Set<RelativeMovement> relativeMovements) {
        player.absMoveTo(x, y, z, yaw, pitch);
        player.serverLevel().getChunkSource().move(player);
    }
}
