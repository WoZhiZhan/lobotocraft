package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class StopContinuousWorkPacket implements IMessage {
    @Override
    public boolean sendToServer() {
        return true;
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
        if (player != null) {
            WorkManager.requestStopContinuousWork(player, "玩家手动停止连续工作");
        }
    }
}
