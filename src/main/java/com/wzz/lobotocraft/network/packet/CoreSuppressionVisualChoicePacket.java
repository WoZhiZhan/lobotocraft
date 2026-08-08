package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class CoreSuppressionVisualChoicePacket implements IMessage {
    private boolean fullEffects;

    public CoreSuppressionVisualChoicePacket(boolean fullEffects) {
        this.fullEffects = fullEffects;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        fullEffects = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(fullEffects);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) CoreSuppressionManager.submitVisualChoice(player, fullEffects);
    }
}
