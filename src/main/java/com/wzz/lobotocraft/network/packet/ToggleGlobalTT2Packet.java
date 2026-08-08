package com.wzz.lobotocraft.network.packet;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

/** Client request to toggle the server-global TT2 work-speed switch. */
public final class ToggleGlobalTT2Packet implements IMessage {
    public ToggleGlobalTT2Packet() {
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !RequestThrottle.allow(player, "toggle-global-tt2", 10L)) return;

        boolean enabled = FacilityDayData.get(player.server.overworld()).toggleTt2Protocol();
        player.displayClientMessage(Component.literal(enabled
                ? "§aTT2协议已对全公司启用。"
                : "§7TT2协议已对全公司关闭。"), true);
        OpenRoomMapPacket.sendTo(player, true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
