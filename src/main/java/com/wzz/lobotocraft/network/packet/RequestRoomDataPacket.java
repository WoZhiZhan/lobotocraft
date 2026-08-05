package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端：监控界面开着的时候定期请求刷新一次房间数据，
 * 这样屏幕上的血量 / Qliphoth / 出逃状态是活的。
 */
public class RequestRoomDataPacket implements IMessage {

    public RequestRoomDataPacket() {
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
        if (player == null) return;
        if (!RequestThrottle.allowSnapshot(player, "room-map")) return;
        OpenRoomMapPacket.sendTo(player, true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
