package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkStartAuthorizations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 开始使用工具数据包（客户端 -> 服务器）
 */
public class StartUsingToolPacket implements IMessage {
    private int abnormalityId;
    private String abnormalityName;

    public StartUsingToolPacket(int abnormalityId, String  abnormalityName) {
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.abnormalityName = buf.readUtf(128);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeUtf(abnormalityName, 128);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // 获取工具异想体实体
            Entity entity = player.level().getEntity(abnormalityId);
            if (!(entity instanceof IAbnormality abnormality)) {
                player.sendSystemMessage(Component.literal("§c工具异想体不存在或已消失"));
                return;
            }

            if (!abnormality.isToolType()) {
                player.sendSystemMessage(Component.literal("§c目标不是工具型异想体"));
                return;
            }

            if (!WorkStartAuthorizations.consumeToolUse(player, entity.getUUID())) {
                player.sendSystemMessage(Component.literal("§c工具授权已失效，请重新与收容单元交互"));
                return;
            }

            if (WorkManager.isPlayerWorking(player)) {
                player.sendSystemMessage(Component.literal("§c你正在进行工作，无法使用工具"));
                return;
            }

            // 检查是否为持续使用型工具
            if (!abnormality.isContinuousUseTool()) {
                player.sendSystemMessage(Component.literal("§c此异想体不是持续使用型工具"));
                return;
            }

            // 检查距离
            if (player.distanceToSqr(entity) > 100.0) {
                player.sendSystemMessage(Component.literal("§c距离工具太远了"));
                return;
            }

            // 开始使用工具
            boolean success = abnormality.startUsing(player);
            MessageLoader.getLoader().sendToPlayer(player,
                    new StartUsingToolResultPacket(
                            success,
                            success ? "" : "§c无法使用此工具",
                            abnormalityId,
                            abnormalityName
                    )
            );
        });
        ctx.setPacketHandled(true);
    }
}
