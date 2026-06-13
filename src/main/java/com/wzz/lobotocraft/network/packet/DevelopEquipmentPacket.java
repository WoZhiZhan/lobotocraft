package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.AbnormalityWorkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 装备研发数据包（客户端 -> 服务器）
 * 用于研发EGO装备（武器或护甲）
 */
public class DevelopEquipmentPacket implements IMessage {
    private String abnormalityCode;
    private String equipmentType;  // "weapon" 或 "armor"
    private int entityId;
    private double scrollOffset;  // 滚动偏移量

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
        this.scrollOffset = buf.readDouble();  // 读取滚动位置
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(equipmentType);
        buf.writeInt(entityId);
        buf.writeDouble(scrollOffset);  // 写入滚动位置
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
                // 异想体装备改为无限制作次数(消耗能源不变),不再限制研发上限

                // 获取研发所需能量
                int cost = equipmentType.equals("weapon") ?
                        abnormality.getWeaponDevelopmentCost() :
                        abnormality.getArmorDevelopmentCost();

                // 检查并消耗PE-BOX
                if (PEBoxItem.consumePEBoxes(player.getInventory(), abnormalityCode, cost)) {
                    giveEquipment(player, abnormality, equipmentType);
                    player.sendSystemMessage(Component.literal(
                            "§a成功研发" + (equipmentType.equals("weapon") ? "武器" : "护甲") +
                                    "！消耗了 " + cost + " 个PE-BOX"
                    ));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§cPE-BOX不足！需要 " + cost + " 个"
                    ));
                }
            });
        });
        ctx.setPacketHandled(true);
    }

    /**
     * 给予玩家装备
     */
    private void giveEquipment(ServerPlayer player, IAbnormality abnormality, String equipmentType) {
        if (equipmentType.equals("weapon")) {
            EGOEquipmentData.WeaponData weaponData = abnormality.getEGOWeaponData();
            if (weaponData == null) return;

            // weaponData.itemId() 已经是完整的武器ID，如 "repentance_weapon"
            ItemStack weapon = new ItemStack(
                    ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(weaponData.itemId()))
            );

            if (!weapon.isEmpty()) {
                player.addItem(weapon);
                player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                        .ifPresent(data -> {
                            data.addEquipmentDevelopmentCount(abnormalityCode, equipmentType, 1);
                            AbnormalityWorkHandler.openAbnormalityEncyclopediaScreen(player, abnormality, data, abnormalityCode, scrollOffset);
                        });
            }

        } else if (equipmentType.equals("armor")) {
            EGOEquipmentData.ArmorData armorData = abnormality.getEGOArmorData();
            if (armorData == null) return;

            // armorData.armorId() 返回基础ID，如 "repentance"
            String baseArmorId = armorData.armorId();
            // 拼接完整的装备ID
            ItemStack chestplate = new ItemStack(
                    ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(baseArmorId + "_chestplate"))
            );
            ItemStack leggings = new ItemStack(
                    ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(baseArmorId + "_leggings"))
            );
            ItemStack boots = new ItemStack(
                    ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(baseArmorId + "_boots"))
            );

            // 给予胸甲
            if (!chestplate.isEmpty()) {
                player.addItem(chestplate);
            }

            // 给予护腿
            if (!leggings.isEmpty()) {
                player.addItem(leggings);
            }

            // 给予靴子
            if (!boots.isEmpty()) {
                player.addItem(boots);
            }
            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                    .ifPresent(data -> {
                        data.addEquipmentDevelopmentCount(abnormalityCode, equipmentType, 1);
                        AbnormalityWorkHandler.openAbnormalityEncyclopediaScreen(player, abnormality, data, abnormalityCode, scrollOffset);
                    });
        }
    }
}