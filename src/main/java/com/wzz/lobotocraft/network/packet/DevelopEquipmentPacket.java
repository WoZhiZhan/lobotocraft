package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoResearchScope;
import com.wzz.lobotocraft.ego.EgoItemSpec;
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
    private String researchScope;

    public DevelopEquipmentPacket() {
    }

    public DevelopEquipmentPacket(String abnormalityCode, String equipmentType, int entityId, double scrollOffset) {
        this(abnormalityCode, equipmentType, entityId, scrollOffset, EgoResearchScope.PERSONAL.id());
    }

    public DevelopEquipmentPacket(String abnormalityCode, String equipmentType, int entityId,
                                  double scrollOffset, String researchScope) {
        this.abnormalityCode = abnormalityCode;
        this.equipmentType = equipmentType;
        this.entityId = entityId;
        this.scrollOffset = scrollOffset;
        this.researchScope = researchScope;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityCode = buf.readUtf(256);
        this.equipmentType = buf.readUtf(256);
        this.entityId = buf.readInt();
        this.scrollOffset = buf.readDouble();
        this.researchScope = buf.readUtf(256);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(equipmentType);
        buf.writeInt(entityId);
        buf.writeDouble(scrollOffset);
        buf.writeUtf(researchScope == null ? EgoResearchScope.PERSONAL.id() : researchScope);
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

            // 距离校验：防止客户端伪造 entityId 远程研发。与 WorkManager 交互距离一致（100.0 = 10 格）。
            if (player.distanceToSqr(entity) > 100.0) {
                player.sendSystemMessage(Component.literal("§c距离该异想体太远！"));
                return;
            }

            // 安全修复：abnormalityCode 由客户端提供，仅用于展示/映射，绝不能作为扣费/上限判定的依据。
            // 用服务端实体的真实 code 覆盖客户端传入值，后续所有逻辑都基于服务端权威值。
            this.abnormalityCode = abnormality.getAbnormalityCode();

            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
                int cost = getDevelopmentCost(abnormality);
                List<ItemStack> rewards = getRewards(abnormality);
                EgoResearchScope scope = EgoResearchScope.byId(researchScope);

                if (rewards.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§c该异想体没有可研发的" + typeName() + "！"));
                    return;
                }

                // 与 PlayerAbnormalityData.researchPersonalEgo 保持同一判据：计数满且确有物品才算已研发。
                // 老存档只有计数没有 personalEgoItems，若只看计数会让那件 E.G.O 永久锁死。
                if (scope == EgoResearchScope.PERSONAL
                        && data.getEquipmentDevelopmentCount(abnormalityCode, equipmentType) >= 1
                        && !data.getPersonalEgoItems(abnormalityCode, equipmentType).isEmpty()) {
                    player.sendSystemMessage(Component.literal("§c个人授权中已研发该E.G.O，每种只能拥有一件。"));
                    return;
                }

                AiEgoRepositoryData repository = AiEgoRepositoryData.get(player.server);
                int maximum = equipmentType.equals("weapon")
                        ? abnormality.getWeaponDevelopmentMaxCount()
                        : abnormality.getArmorDevelopmentMaxCount();
                if (scope == EgoResearchScope.AI_GLOBAL
                        && repository.total(abnormalityCode, equipmentType) >= Math.max(1, maximum)) {
                    player.sendSystemMessage(Component.literal("§cAI全局库存已达到该E.G.O的研发上限。"));
                    return;
                }

                if (!PEBoxItem.consumePEBoxes(player.getInventory(), abnormalityCode, cost)) {
                    player.sendSystemMessage(Component.literal("§cPE-BOX不足！需要 " + cost + " 个"));
                    return;
                }

                boolean researched = scope == EgoResearchScope.PERSONAL
                        ? data.researchPersonalEgoSpecs(abnormalityCode, equipmentType,
                                EgoItemSpec.fromStacks(rewards))
                        : repository.research(abnormalityCode, equipmentType, rewards, maximum);
                if (!researched) return;

                player.sendSystemMessage(Component.literal(
                        "§a成功研发" + typeName() + "至"
                                + (scope == EgoResearchScope.PERSONAL ? "个人授权" : "AI全局库存")
                                + "！消耗了 " + cost + " 个PE-BOX"
                ));

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

}
