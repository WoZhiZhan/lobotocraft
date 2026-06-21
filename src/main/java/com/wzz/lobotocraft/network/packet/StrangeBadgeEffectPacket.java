package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.StrangeBadgeEffectHandler;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class StrangeBadgeEffectPacket implements IMessage {
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        StrangeBadgeEffectHandler.triggerEffect();
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
