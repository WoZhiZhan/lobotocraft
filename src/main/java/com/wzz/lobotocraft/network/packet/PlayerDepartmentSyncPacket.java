package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.PlayerDepartmentClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public final class PlayerDepartmentSyncPacket implements IMessage {
    private UUID playerId;
    private String departmentId;

    public PlayerDepartmentSyncPacket() {
    }

    public PlayerDepartmentSyncPacket(UUID playerId, String departmentId) {
        this.playerId = playerId;
        this.departmentId = departmentId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        playerId = buffer.readUUID();
        departmentId = buffer.readUtf(32);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerId);
        buffer.writeUtf(departmentId, 32);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        PlayerDepartmentClientState.update(playerId, departmentId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            var player = minecraft.level.getPlayerByUUID(playerId);
            if (player != null) player.refreshDisplayName();
        }
    }
}
