package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkManager;
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
        this.workType = WorkType.values()[buf.readInt()];
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

            // 获取异想体实体
            Entity entity = player.level().getEntity(abnormalityId);
            if (!(entity instanceof AbstractAbnormality abnormality)) {
                player.sendSystemMessage(Component.literal("§c异想体不存在或已消失"));
                return;
            }

            // 检查距离
            if (player.distanceToSqr(entity) > 100.0) {
                player.sendSystemMessage(Component.literal("§c距离异想体太远了"));
                return;
            }

            // 检查玩家是否已经在工作中
            if (WorkManager.isPlayerWorking(player)) {
                player.sendSystemMessage(Component.literal("§c你已经在进行工作了"));
                return;
            }
            WorkManager.startWork(player, abnormality, workType);
        });
        ctx.setPacketHandled(true);
    }
}