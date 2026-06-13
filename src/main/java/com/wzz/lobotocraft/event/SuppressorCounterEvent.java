package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.SuppressorCounterUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 便携式抑制计数(HUD 机制)的服务端逻辑:
 *  - 公司维度外每 8 秒(160 tick)消耗 1 点
 *  - 玩家受到伤害后,按伤害数值额外扣减对应抑制计数
 *  - 计数归零时,施加与原"便携式抑制器"道具相同的惩罚(四色伤害 + 缓慢)
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SuppressorCounterEvent {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative() || player.isSpectator()) return;
        // 仅在公司维度之外生效
        if (player.level().dimension() == ModDimensions.LOBOTO_KEY) return;

        if (player.tickCount % 160 == 0 && !player.isDeadOrDying()) {
            int v = SuppressorCounterUtil.get(player);
            if (v > 0) {
                SuppressorCounterUtil.reduce(player, 1);
            } else {
                applyEmptyPenalty(player);
            }
        }
        // 每秒同步计数到客户端用于 HUD 显示
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && player.tickCount % 20 == 0) {
            com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(sp,
                    new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(SuppressorCounterUtil.get(player)));
        }
    }

    /** 受到伤害后,按伤害数值额外扣减抑制计数 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (player.level().dimension() == ModDimensions.LOBOTO_KEY) return;
        // 避免惩罚自身造成的伤害递归扣减
        if (event.getSource() != null && event.getSource().getEntity() == player) return;
        int amount = Math.max(1, Math.round(event.getAmount()));
        SuppressorCounterUtil.reduce(player, amount);
    }

    /** 捕捉单元合成前置:抑制计数需为满(120),合成后消耗所有计数;否则取消合成并退还能源 */
    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(event.getCrafting().getItem() instanceof com.wzz.lobotocraft.item.CaptureUnitItem)) return;

        if (SuppressorCounterUtil.get(player) >= SuppressorCounterUtil.MAX_COUNT) {
            // 满足条件:消耗所有抑制计数
            SuppressorCounterUtil.set(player, 0);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(sp,
                        new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(0));
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b捕捉单元制作完成,抑制计数已耗尽。"));
            }
        } else {
            // 计数不足:取消合成,移除产出并退还全部材料
            // 配方材料:金锭x2 + 铁块x3 + 黑曜石x3 + 异想体能源(pe_box)x1
            event.getCrafting().setCount(0);
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 2));
            inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BLOCK, 3));
            inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OBSIDIAN, 3));
            inv.add(new net.minecraft.world.item.ItemStack(
                    com.wzz.lobotocraft.init.ModItems.PE_BOX.get(), 1));
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c抑制计数未满(需120),无法制作捕捉单元,材料已退还。"));
            }
        }
    }

    /** 与不同等级异想体工作完成,回复对应抑制计数 */
    @SubscribeEvent
    public static void onWorkComplete(com.wzz.lobotocraft.event.work.WorkCompleteEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        String riskLevel = String.valueOf(event.getAbnormality().getRiskLevel());
        int restore = SuppressorCounterUtil.restoreAmountForRiskLevel(riskLevel);
        if (restore > 0) {
            SuppressorCounterUtil.restore(player, restore);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(sp,
                        new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(SuppressorCounterUtil.get(player)));
            }
        }
    }

    private static void applyEmptyPenalty(Player player) {
        // 与原便携式抑制器耗尽时一致:四色伤害各一次 + 缓慢
        EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "red"), 3f + player.getRandom().nextInt(2)));
        EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "white"), 3f + player.getRandom().nextInt(2)));
        EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "black"), 3f + player.getRandom().nextInt(2)));
        EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "blue"), 3f + player.getRandom().nextInt(2)));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 255));
    }
}
