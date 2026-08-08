package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.SuppressorCounterUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 便携式抑制计数(HUD 机制)的服务端逻辑:
 *  - 公司维度外每 8 秒(160 tick)消耗 1 点
 *  - 计数归零时,施加与原"便携式抑制器"道具相同的惩罚(四色伤害 + 缓慢)
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SuppressorCounterEvent {

    private static final int HOSTILE_KILL_RESTORE = 5;

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
                    new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(
                            SuppressorCounterUtil.get(player), SuppressorCounterUtil.capacity(player)));
        }
    }

    /** Restore a small amount of outside-time energy for a real player's hostile-mob kill. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity defeated = event.getEntity();
        if (defeated.level().isClientSide || !isHostile(defeated)) return;

        Player killer = event.getSource().getEntity() instanceof Player sourcePlayer
                ? sourcePlayer
                : defeated.getKillCredit() instanceof Player creditedPlayer ? creditedPlayer : null;
        if (!(killer instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || killer instanceof EmployeeServerPlayer
                || killer instanceof FakePlayer
                || killer.isCreative()
                || killer.isSpectator()) {
            return;
        }

        SuppressorCounterUtil.restore(serverPlayer, HOSTILE_KILL_RESTORE);
        com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(serverPlayer,
                new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(
                        SuppressorCounterUtil.get(serverPlayer), SuppressorCounterUtil.capacity(serverPlayer)));
    }

    private static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy
                || entity instanceof OrdealEntity
                || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    /** 捕捉单元合成:结果槽负责前置拦截;合法制作后消耗全部抑制计数。 */
    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(event.getCrafting().getItem() instanceof com.wzz.lobotocraft.item.CaptureUnitItem)) return;

        int capacity = SuppressorCounterUtil.capacity(player);
        if (SuppressorCounterUtil.get(player) >= capacity) {
            // 满足条件:消耗所有抑制计数
            SuppressorCounterUtil.set(player, 0);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(sp,
                        new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(0, capacity));
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b捕捉单元制作完成,抑制计数已耗尽。"));
            }
        } else {
            // Defensive fallback only. ResultSlot pickup should have rejected this craft already.
            event.getCrafting().setCount(0);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c抑制计数未满(需" + capacity + "),无法制作捕捉单元。"));
            }
        }
    }

    /** 与不同等级异想体工作完成,回复对应抑制计数 */
    @SubscribeEvent
    public static void onWorkComplete(WorkCompleteEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        String riskLevel = String.valueOf(event.getAbnormality().getRiskLevel());
        int restore = SuppressorCounterUtil.restoreAmountForRiskLevel(riskLevel);
        if (restore > 0) {
            SuppressorCounterUtil.restore(player, restore);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(sp,
                        new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(
                                SuppressorCounterUtil.get(player), SuppressorCounterUtil.capacity(player)));
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
