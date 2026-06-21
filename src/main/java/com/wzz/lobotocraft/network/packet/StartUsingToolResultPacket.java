package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.ToolUsageProgressScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class StartUsingToolResultPacket implements IMessage {
    private boolean success;
    private String message;
    private int abnormalityId;
    private String abnormalityName;

    public StartUsingToolResultPacket(boolean success, String message, int abnormalityId, String abnormalityName) {
        this.success = success;
        this.message = message;
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
    }

    public StartUsingToolResultPacket() {}

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        success = buf.readBoolean();
        message = buf.readUtf(32767);
        abnormalityId = buf.readInt();
        abnormalityName = buf.readUtf();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(message);
        buf.writeInt(abnormalityId);
        buf.writeUtf(abnormalityName);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (!success) {
            if (Minecraft.getInstance().player != null && !message.isEmpty()) {
                Minecraft.getInstance().player
                        .sendSystemMessage(Component.literal(message));
            }
            Minecraft.getInstance().setScreen(null);
        } else {
            Minecraft.getInstance().setScreen(new ToolUsageProgressScreen(
                    abnormalityId, abnormalityName
            ));
        }
    }
}
