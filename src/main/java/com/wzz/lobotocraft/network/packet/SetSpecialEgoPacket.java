package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.ego.SpecialEgoCatalog;
import com.wzz.lobotocraft.ego.SpecialEgoService;
import com.wzz.lobotocraft.employee.PlayerProfileService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class SetSpecialEgoPacket implements IMessage {
    private String specialEgoId;
    private String equipmentType;
    private boolean equipped;

    public SetSpecialEgoPacket() {
    }

    public SetSpecialEgoPacket(String specialEgoId, String equipmentType, boolean equipped) {
        this.specialEgoId = specialEgoId;
        this.equipmentType = equipmentType;
        this.equipped = equipped;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        specialEgoId = buffer.readUtf(64);
        equipmentType = buffer.readUtf(8);
        equipped = buffer.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(specialEgoId, 64);
        buffer.writeUtf(equipmentType, 8);
        buffer.writeBoolean(equipped);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || SpecialEgoCatalog.byId(specialEgoId) == null
                || !com.wzz.lobotocraft.ego.SpecialEgoRegistryData.validType(equipmentType)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())) return;
        SpecialEgoService.EquipResult result = SpecialEgoService.setEquipped(
                player, specialEgoId, equipmentType, equipped);
        Component typeName = Component.translatable("weapon".equals(equipmentType)
                ? "equipment.lobotocraft.weapon" : "equipment.lobotocraft.armor");
        Component message = switch (result) {
            case SUCCESS -> Component.translatable(equipped
                    ? "message.lobotocraft.special_ego.claimed"
                    : "message.lobotocraft.special_ego.released", typeName);
            case IN_USE_BY_OTHER -> Component.translatable(
                    "message.lobotocraft.special_ego.in_use", typeName);
            case NOT_ASSIGNED -> Component.translatable(
                    "message.lobotocraft.special_ego.not_assigned", typeName);
            case LOCKED -> Component.translatable("message.lobotocraft.special_ego.locked");
            case REQUIREMENTS_NOT_MET -> Component.translatable(
                    "message.lobotocraft.special_ego.requirements", typeName);
            default -> Component.translatable("message.lobotocraft.special_ego.invalid");
        };
        player.sendSystemMessage(message);
        PlayerProfileService.open(player, PlayerProfileService.View.SPECIAL_EGO);
    }
}
