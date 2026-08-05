package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.llm.LlmConfigService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class RequestLlmConfigPacket implements IMessage {
    @Override public void fromBytes(FriendlyByteBuf buf) { }
    @Override public void toBytes(FriendlyByteBuf buf) { }
    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())
                || !com.wzz.lobotocraft.network.RequestThrottle.allowSnapshot(player, "llm_config")) {
            return;
        }
        LlmConfigService.open(player);
    }
}
