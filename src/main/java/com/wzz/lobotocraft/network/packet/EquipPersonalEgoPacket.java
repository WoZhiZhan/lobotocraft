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
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.personal_ego.special_conflict",
                    Component.translatable("weapon".equals(equipmentType)
                            ? "equipment.lobotocraft.weapon" : "equipment.lobotocraft.armor")));
            return;
        }
        if (abnormalityCode == null || abnormalityCode.isBlank()) {
            if (EgoLoadoutService.unequipPersonal(player, equipmentType)) {
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.personal_ego.unequipped"));
                FacilityManagementService.open(player);
            }
            return;
        }
        if (EgoLoadoutService.equipPersonal(player, equipmentType, abnormalityCode)) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.personal_ego.equipped"));
            FacilityManagementService.open(player);
        } else {
            var missing = EgoLoadoutService.personalMissingRequirements(player, equipmentType, abnormalityCode);
            player.sendSystemMessage(Component.translatable(missing.isEmpty()
                    ? "message.lobotocraft.personal_ego.not_researched"
                    : "message.lobotocraft.personal_ego.requirements"));
        }
    }
}
