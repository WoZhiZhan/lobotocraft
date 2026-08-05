package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.SpecialEgoService;
import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class EquipPersonalEgoPacket implements IMessage {
    private String abnormalityCode;
    private String equipmentType;

    public EquipPersonalEgoPacket() {
    }

    public EquipPersonalEgoPacket(String abnormalityCode, String equipmentType) {
        this.abnormalityCode = abnormalityCode;
        this.equipmentType = equipmentType;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        abnormalityCode = buf.readUtf(256);
        equipmentType = buf.readUtf(256);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(equipmentType);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())) return;
        if (!"weapon".equals(equipmentType) && !"armor".equals(equipmentType)) return;
        if (SpecialEgoService.hasEquippedSpecial(player, equipmentType)) {
            player.sendSystemMessage(Component.literal("§c请先在特殊 E.G.O 界面卸下当前"
                    + ("weapon".equals(equipmentType) ? "武器" : "护甲") + "。"));
            return;
        }
        if (abnormalityCode == null || abnormalityCode.isBlank()) {
            if (EgoLoadoutService.unequipPersonal(player, equipmentType)) {
                player.sendSystemMessage(Component.literal("§a已卸下个人 E.G.O。"));
                FacilityManagementService.open(player);
            }
            return;
        }
        if (EgoLoadoutService.equipPersonal(player, equipmentType, abnormalityCode)) {
            player.sendSystemMessage(Component.literal("§a已应用个人E.G.O装备方案。"));
            FacilityManagementService.open(player);
        } else {
            var missing = EgoLoadoutService.personalMissingRequirements(player, equipmentType, abnormalityCode);
            player.sendSystemMessage(Component.literal(missing.isEmpty()
                    ? "§c无法应用：该E.G.O尚未由你研发。"
                    : "§c无法应用，等级不足：" + String.join("，", missing)));
        }
    }
}
