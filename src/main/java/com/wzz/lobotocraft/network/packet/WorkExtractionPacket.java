package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.WorkProgressScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 工作提取数据包（服务器 -> 客户端）
 * 每次能量提取时发送，通知客户端更新UI
 */
public class WorkExtractionPacket implements IMessage {
    private int abnormalityId;
    private WorkType workType;
    private boolean success;
    private int extractionCount;    // 当前提取次数（1-5）
    private int successCount;       // 成功总数
    private int failureCount;       // 失败总数
    private float speedMultiplier;
    
    public WorkExtractionPacket(int abnormalityId, WorkType workType, boolean success,
                               int extractionCount, int successCount, int failureCount, float speedMultiplier) {
        this.abnormalityId = abnormalityId;
        this.workType = workType;
        this.success = success;
        this.extractionCount = extractionCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.speedMultiplier = speedMultiplier;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.workType = WorkType.values()[buf.readInt()];
        this.success = buf.readBoolean();
        this.extractionCount = buf.readInt();
        this.successCount = buf.readInt();
        this.failureCount = buf.readInt();
        this.speedMultiplier = buf.readFloat();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(workType.ordinal());
        buf.writeBoolean(success);
        buf.writeInt(extractionCount);
        buf.writeInt(successCount);
        buf.writeInt(failureCount);
        buf.writeFloat(speedMultiplier);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // 获取当前打开的界面
            Screen currentScreen = Minecraft.getInstance().screen;
            
            if (currentScreen instanceof WorkProgressScreen screen) {
                // 更新UI
                screen.onExtractionReceived(success, extractionCount, successCount, failureCount);
                screen.setSpeedMultiplier(speedMultiplier);
            }
        });
        ctx.setPacketHandled(true);
    }
}