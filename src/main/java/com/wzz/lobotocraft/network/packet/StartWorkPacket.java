package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkStartAuthorizations;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 开始工作数据包（客户端 -> 服务器）
 * 修正版：删除了重复调用
 */
public class StartWorkPacket implements IMessage {
    private int abnormalityId;
    private WorkType workType;

    public StartWorkPacket(int abnormalityId, WorkType workType) {
        this.abnormalityId = abnormalityId;
        this.workType = workType;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        int ordinal = buf.readInt();
        WorkType[] values = WorkType.values();
        this.workType = ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(workType.ordinal());
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (workType == null) return;

            // 获取异想体实体
            Entity entity = player.level().getEntity(abnormalityId);
            if (!(entity instanceof AbstractAbnormality abnormality)) {
                player.sendSystemMessage(Component.translatable("message.lobotocraft.work.abnormality_missing"));
                return;
            }

            if (abnormality.isToolType()) {
                player.sendSystemMessage(Component.translatable("message.lobotocraft.work.tool_normal_forbidden"));
                return;
            }

            if (!WorkStartAuthorizations.consume(player, abnormality.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.lobotocraft.work.authorization_expired"));
                return;
            }

            // 检查距离
            if (player.distanceToSqr(entity) > 100.0) {
                player.sendSystemMessage(Component.translatable("message.lobotocraft.work.too_far"));
                return;
            }

            // 检查玩家是否已经在工作中
            if (WorkManager.isPlayerWorking(player)) {
                player.sendSystemMessage(Component.translatable("message.lobotocraft.work.already_working"));
                return;
            }
            WorkManager.startWork(player, abnormality, workType);
        });
        ctx.setPacketHandled(true);
    }
}
