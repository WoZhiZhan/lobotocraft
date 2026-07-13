package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.AbnormalityWorkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;

/**
 * 装备研发数据包（客户端 -> 服务器）
 * 用于研发EGO装备（武器 / 护甲）
 * <p>
 * 改动点：不再在这里硬编码"武器发一件、护甲发三件"，
 * 而是统一向 IAbnormality 要一个 List&lt;ItemStack&gt;。
 * 这样多武器 / 多饰品的异想体只要覆写 getEGOWeaponStacks() 就行，本类不用动。
 */
public class DevelopEquipmentPacket implements IMessage {
    private String abnormalityCode;
    private String equipmentType;  // "weapon" / "armor" / "gift"
    private int entityId;
    private double scrollOffset;   // 滚动偏移量

    public DevelopEquipmentPacket() {
    }

    public DevelopEquipmentPacket(String abnormalityCode, String equipmentType, int entityId, double scrollOffset) {
        this.abnormalityCode = abnormalityCode;
        this.equipmentType = equipmentType;
        this.entityId = entityId;
        this.scrollOffset = scrollOffset;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityCode = buf.readUtf();
        this.equipmentType = buf.readUtf();
        this.entityId = buf.readInt();
        this.scrollOffset = buf.readDouble();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(equipmentType);
        buf.writeInt(entityId);
        buf.writeDouble(scrollOffset);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Entity entity = player.level.getEntity(entityId);

            if (!(entity instanceof IAbnormality abnormality)) {
                player.sendSystemMessage(Component.literal("§c找不到对应的异想体！"));
                return;
            }

            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
                int cost = getDevelopmentCost(abnormality);
                List<ItemStack> rewards = getRewards(abnormality);

                if (rewards.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§c该异想体没有可研发的" + typeName() + "！"));
                    return;
                }

                if (!PEBoxItem.consumePEBoxes(player.getInventory(), abnormalityCode, cost)) {
                    player.sendSystemMessage(Component.literal("§cPE-BOX不足！需要 " + cost + " 个"));
                    return;
                }

                for (ItemStack stack : rewards) {
                    giveOrDrop(player, stack);
                }

                player.sendSystemMessage(Component.literal(
                        "§a成功研发" + typeName() + "！消耗了 " + cost + " 个PE-BOX"
                ));

                data.addEquipmentDevelopmentCount(abnormalityCode, equipmentType, 1);
                AbnormalityWorkHandler.openAbnormalityEncyclopediaScreen(
                        player, abnormality, data, abnormalityCode, scrollOffset);
            });
        });
        ctx.setPacketHandled(true);
    }

    /**
     * 本次研发要发放的所有物品。
     * 多武器 / 多饰品的异想体在 IAbnormality 里覆写对应方法，这里不用改。
     */
    private List<ItemStack> getRewards(IAbnormality abnormality) {
        return switch (equipmentType) {
            case "weapon" -> abnormality.getEGOWeaponStacks();
            case "armor" -> abnormality.getEGOArmorStacks();
            default -> List.of();
        };
    }

    private int getDevelopmentCost(IAbnormality abnormality) {
        return equipmentType.equals("weapon")
                ? abnormality.getWeaponDevelopmentCost()
                : abnormality.getArmorDevelopmentCost();
    }

    private String typeName() {
        return switch (equipmentType) {
            case "weapon" -> "武器";
            case "armor" -> "护甲";
            default -> "装备";
        };
    }

    /** 背包塞不下就掉在脚下，别把研发出来的东西吞了 */
    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}