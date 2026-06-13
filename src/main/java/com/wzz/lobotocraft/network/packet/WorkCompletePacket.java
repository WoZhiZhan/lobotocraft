package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.WorkProgressScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 工作完成数据包（服务器 -> 客户端）
 * 工作完成时发送，通知客户端显示最终结果
 */
public class WorkCompletePacket implements IMessage {
    private int abnormalityId;
    private WorkType workType;
    private WorkResult result;
    private int peOutput;
    private int successCount;
    private int failureCount;
    
    public WorkCompletePacket(int abnormalityId, WorkType workType, WorkResult result,
                            int peOutput, int successCount, int failureCount) {
        this.abnormalityId = abnormalityId;
        this.workType = workType;
        this.result = result;
        this.peOutput = peOutput;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.workType = WorkType.values()[buf.readInt()];
        this.result = WorkResult.values()[buf.readInt()];
        this.peOutput = buf.readInt();
        this.successCount = buf.readInt();
        this.failureCount = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(workType.ordinal());
        buf.writeInt(result.ordinal());
        buf.writeInt(peOutput);
        buf.writeInt(successCount);
        buf.writeInt(failureCount);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // 获取当前打开的界面
            Screen currentScreen = Minecraft.getInstance().screen;
            
            if (currentScreen instanceof WorkProgressScreen screen) {
                // 显示最终结果
                screen.onWorkComplete(result, peOutput);
            }
        });
        ctx.setPacketHandled(true);
    }
}