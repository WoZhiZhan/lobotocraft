package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.WelcomeNoticeClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/** Opens the first-save notice on the joining player's client. */
public final class OpenWelcomeNoticePacket implements IMessage {
    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        WelcomeNoticeClientState.requestOpen();
    }
}
