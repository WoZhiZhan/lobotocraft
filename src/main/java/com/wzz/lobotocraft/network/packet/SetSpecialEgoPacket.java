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
        String typeName = "weapon".equals(equipmentType) ? "武器" : "护甲";
        Component message = switch (result) {
            case SUCCESS -> Component.literal(equipped
                    ? "§d已取得特殊 E.G.O " + typeName + "席位。"
                    : "§a已释放特殊 E.G.O " + typeName + "席位，并恢复同类个人 E.G.O。" );
            case IN_USE_BY_OTHER -> Component.literal("§c该特殊 E.G.O 的" + typeName
                    + "正由其他玩家使用。" );
            case NOT_ASSIGNED -> Component.literal("§7你没有占用该特殊 E.G.O 的" + typeName + "席位。" );
            case LOCKED -> Component.literal("§c该特殊 E.G.O 尚未解锁。" );
            case REQUIREMENTS_NOT_MET -> Component.literal("§c属性等级不足，无法使用该特殊 E.G.O "
                    + typeName + "。" );
            default -> Component.literal("§c特殊 E.G.O 状态无效。" );
        };
        player.sendSystemMessage(message);
        PlayerProfileService.open(player, PlayerProfileService.View.SPECIAL_EGO);
    }
}
