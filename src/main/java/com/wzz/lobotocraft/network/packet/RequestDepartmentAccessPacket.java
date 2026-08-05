package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.llm.LlmDepartmentAccessService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class RequestDepartmentAccessPacket implements IMessage {
    @Override public void fromBytes(FriendlyByteBuf buffer) { }
    @Override public void toBytes(FriendlyByteBuf buffer) { }
    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())
                || !RequestThrottle.allowSnapshot(player, "llm_department_access")) return;
        LlmDepartmentAccessService.open(player);
    }
}
