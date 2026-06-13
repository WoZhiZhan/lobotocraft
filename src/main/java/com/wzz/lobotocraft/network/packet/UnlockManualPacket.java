package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;

/**
 * 解锁管理须知数据包（客户端 -> 服务器）
 * 解锁后观察等级自动提升（等于解锁数量）
 */
public class UnlockManualPacket implements IMessage {
    private String abnormalityCode;
    private int entryIndex;
    private int peBoxCost;

    public UnlockManualPacket(String abnormalityCode, int entryIndex, int peBoxCost) {
        this.abnormalityCode = abnormalityCode;
        this.entryIndex = entryIndex;
        this.peBoxCost = peBoxCost;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityCode = buf.readUtf();
        this.entryIndex = buf.readInt();
        this.peBoxCost = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode);
        buf.writeInt(entryIndex);
        buf.writeInt(peBoxCost);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 检查并消耗PE-BOX
                if (PEBoxItem.consumePEBoxes(player.getInventory(), abnormalityCode, peBoxCost)) {
                    // 解锁管理须知
                    player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                            .ifPresent(data -> {
                                // 解锁
                                data.unlockManual(abnormalityCode, entryIndex);
                                AABB box = player.getBoundingBox().inflate(64);
                                List<AbstractAbnormality> list = player.level().getEntitiesOfClass(AbstractAbnormality.class, box);
                                for (AbstractAbnormality ent : list) {
                                    if (Objects.equals(ent.getAbnormalityCode(), this.abnormalityCode)) {
                                        ent.sendPerPlayerName(player, Component.literal(ent.getAbnormalityName()));
                                    }
                                }
                                // 获取当前观察等级（等于解锁数量）
                                int currentLevel = data.getObservationLevel(abnormalityCode);

                                // 发送成功消息
                                player.sendSystemMessage(Component.literal(
                                        "§a成功解锁！消耗了 " + peBoxCost + " 个PE-BOX"
                                ));
                                player.sendSystemMessage(Component.literal(
                                        "§b观察等级: " + currentLevel
                                ));
                            });
                } else {
                    // PE-BOX不足
                    player.sendSystemMessage(Component.literal(
                            "§cPE-BOX不足！需要 " + peBoxCost + " 个"
                    ));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}