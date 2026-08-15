package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.ToolUsageProgressScreen;
import com.wzz.lobotocraft.client.AbnormalityNameResolver;
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
    private String abnormalityNameKey;

    public StartUsingToolResultPacket(boolean success, String message, int abnormalityId,
                                      String abnormalityName, String abnormalityNameKey) {
        this.success = success;
        this.message = message;
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
        this.abnormalityNameKey = abnormalityNameKey;
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
        abnormalityNameKey = buf.readUtf(128);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(message);
        buf.writeInt(abnormalityId);
        buf.writeUtf(abnormalityName);
        buf.writeUtf(abnormalityNameKey == null ? "" : abnormalityNameKey, 128);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (!success) {
            if (Minecraft.getInstance().player != null && !message.isEmpty()) {
                Minecraft.getInstance().player
                        .sendSystemMessage(Component.translatable(message));
            }
            Minecraft.getInstance().setScreen(null);
        } else {
            Minecraft.getInstance().setScreen(new ToolUsageProgressScreen(
                    abnormalityId, AbnormalityNameResolver.resolve(
                            abnormalityId, abnormalityNameKey, abnormalityName)
            ));
        }
    }
}
