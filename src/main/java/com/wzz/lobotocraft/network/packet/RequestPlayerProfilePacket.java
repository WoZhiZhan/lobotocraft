package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.PlayerProfileService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class RequestPlayerProfilePacket implements IMessage {
    private String viewId;

    public RequestPlayerProfilePacket() {
    }

    public RequestPlayerProfilePacket(PlayerProfileService.View view) {
        this.viewId = view.id();
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        viewId = buffer.readUtf(24);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(viewId, 24);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        PlayerProfileService.View view = PlayerProfileService.View.byId(viewId);
        if (player == null || view == null
                || !RequestThrottle.allowSnapshot(player, "player_profile_" + view.id())) return;
        PlayerProfileService.open(player, view);
    }
}
