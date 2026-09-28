package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;


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
        this.abnormalityCode = buf.readUtf(64);
        this.entryIndex = buf.readInt();
        this.peBoxCost = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode, 64);
        buf.writeInt(entryIndex);
        buf.writeInt(peBoxCost);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                unlockUsingServerData(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    /** The client chooses an entry, but never its price or abnormality data. */
    private void unlockUsingServerData(ServerPlayer player) {
        if (!RequestThrottle.allow(player, "unlock-manual", 150L)
                || abnormalityCode == null || abnormalityCode.isBlank()) return;
        AbstractAbnormality abnormality = findLoadedAbnormality(player, abnormalityCode);
        if (abnormality == null || !isValidEntry(abnormalityCode, entryIndex)) return;
        int cost = serverCost(abnormality, entryIndex);
        if (cost < 0 || cost > 1024) return;
        com.wzz.lobotocraft.work.FacilityKnowledgeService.synchronize(player);
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
            if (data.isManualUnlocked(abnormalityCode, entryIndex)) return;
            if (!PEBoxItem.consumePEBoxes(player.getInventory(), abnormalityCode, cost)) {
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.manual.insufficient", cost));
                return;
            }
            com.wzz.lobotocraft.work.FacilityKnowledgeService.unlock(player, abnormalityCode, entryIndex);
            abnormality.sendPerPlayerName(player, abnormality.getLocalizedAbnormalityName());
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.manual.entry_unlocked"));
        });
    }

    private static AbstractAbnormality findLoadedAbnormality(ServerPlayer player, String code) {
        for (var level : player.server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof AbstractAbnormality abnormality
                        && abnormality.isAlive() && code.equals(abnormality.getAbnormalityCode())) {
                    return abnormality;
                }
            }
        }
        return null;
    }

    private static boolean isValidEntry(String code, int entryIndex) {
        if (entryIndex == 0 || entryIndex == 2 || (entryIndex >= 100 && entryIndex <= 103)) return true;
        int count = AbnormalityEncyclopediaData.getData(code).getManualCount();
        return entryIndex >= 3 && entryIndex < 3 + count;
    }

    private static int serverCost(AbstractAbnormality abnormality, int entryIndex) {
        if (entryIndex == 0) return abnormality.getBasicInfoCost();
        if (entryIndex == 2) return abnormality.getSensitiveInfoCost();
        if (entryIndex >= 100 && entryIndex <= 103) return abnormality.getWorkPreferencesCost();
        return abnormality.getManualCost(entryIndex - 3);
    }
}
