package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 停止使用工具数据包（客户端 -> 服务器）
 * 用于持续使用型工具的主动停止
 */
public class StopUsingToolPacket implements IMessage {
    private int abnormalityId;

    public StopUsingToolPacket(int abnormalityId) {
        this.abnormalityId = abnormalityId;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // 获取工具异想体实体
            Entity entity = player.level().getEntity(abnormalityId);
            if (entity instanceof IAbnormality abnormality) {
                // 检查是否为持续使用型工具
                if (abnormality.isContinuousUseTool()) {
                    // 调用接口方法停止使用
                    abnormality.stopUsing(player);
                } else {
                    player.sendSystemMessage(Component.literal("§c此异想体不是持续使用型工具"));
                }
            } else {
                player.sendSystemMessage(Component.literal("§c工具异想体不存在或已消失"));
            }
        });
        ctx.setPacketHandled(true);
    }
}