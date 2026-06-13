package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.StrangeBadgeEffectHandler;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class StrangeBadgeEffectPacket implements IMessage {
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(StrangeBadgeEffectHandler::triggerEffect);
        ctx.setPacketHandled(true);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
