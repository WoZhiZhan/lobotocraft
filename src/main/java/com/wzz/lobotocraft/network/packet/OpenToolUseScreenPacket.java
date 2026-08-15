package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.ToolUseScreen;
import com.wzz.lobotocraft.client.AbnormalityNameResolver;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 打开工具使用界面数据包（服务器 -> 客户端）
 */
public class OpenToolUseScreenPacket implements IMessage {
    private static final int MAX_WARNING_MESSAGES = 64;
    private int abnormalityId;
    private String abnormalityName;
    private String abnormalityNameKey;
    private String toolType;
    private String warningTitle;
    private String[] warningMessages;

    public OpenToolUseScreenPacket(int abnormalityId, String abnormalityName,
                                   String abnormalityNameKey, String toolType,
                                   String warningTitle, String[] warningMessages) {
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
        this.abnormalityNameKey = abnormalityNameKey;
        this.toolType = toolType;
        this.warningTitle = warningTitle;
        this.warningMessages = warningMessages;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.abnormalityName = buf.readUtf();
        this.abnormalityNameKey = buf.readUtf(128);
        this.toolType = buf.readUtf();
        this.warningTitle = buf.readUtf();
        int messageCount = PacketDecodeLimits.readIntCount(
                buf, MAX_WARNING_MESSAGES, "tool warning message");
        this.warningMessages = new String[messageCount];
        for (int i = 0; i < messageCount; i++) {
            this.warningMessages[i] = buf.readUtf();
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeUtf(abnormalityName);
        buf.writeUtf(abnormalityNameKey == null ? "" : abnormalityNameKey, 128);
        buf.writeUtf(toolType);
        buf.writeUtf(warningTitle);
        int messageCount = Math.min(warningMessages.length, MAX_WARNING_MESSAGES);
        buf.writeInt(messageCount);
        for (int i = 0; i < messageCount; i++) {
            buf.writeUtf(warningMessages[i]);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().setScreen(new ToolUseScreen(
                abnormalityId, AbnormalityNameResolver.resolve(
                        abnormalityId, abnormalityNameKey, abnormalityName),
                toolType, warningTitle, warningMessages
        ));
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
