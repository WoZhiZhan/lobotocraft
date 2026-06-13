package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.util.ItemUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 公司睡眠系统事件处理
 * 功能：
 * 1. 在公司维度允许睡觉（即使是永夜）
 * 2. 检查工作次数是否达标
 * 3. 睡醒后进入新的一天，清除装备锁定
 */
@Mod.EventBusSubscriber
public class CompanySleepHandler {

    /**
     * 处理玩家尝试睡觉事件
     */
    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        
        // 只在服务端处理
        if (player.level().isClientSide) {
            return;
        }

        // 检查是否在公司维度
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            return; // 不在公司，使用原版睡眠规则
        }

        // 在公司维度，检查工作次数
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            int required = data.getRequiredWorkCount();
            int current = data.getTodayWorkCount();

            if (!data.hasCompletedTodayWork()) {
                // 工作次数不足，阻止睡觉
                event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
                
                player.sendSystemMessage(Component.literal(
                        String.format("§c你还需要完成 %d 次工作才能休息！ §7(%d/%d)", 
                                required - current, current, required)
                ).withStyle(ChatFormatting.RED));
                
                player.sendSystemMessage(Component.literal(
                        "§7提示：对异想体完成一次完整工作即可增加工作次数。"
                ).withStyle(ChatFormatting.GRAY));
            } else {
                event.setResult((Player.BedSleepingProblem) null); // 不设置问题，允许睡觉
                
                player.sendSystemMessage(Component.literal(
                        "§a今日工作已完成，好好休息吧..."
                ).withStyle(ChatFormatting.GREEN));
            }
        });
    }

    @SubscribeEvent
    public static void onSleepingTimeCheckEvent(SleepingTimeCheckEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            return;
        }
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            if (data.hasCompletedTodayWork()) {
                event.setResult(Event.Result.ALLOW);
            }
        });
    }

    /**
     * 处理玩家醒来事件
     */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        
        // 只在服务端处理
        if (player.level().isClientSide) {
            return;
        }

        // 检查是否在公司维度
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            return;
        }
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            data.setOwner((ServerPlayer) player);
            if (data.hasCompletedTodayWork()) {
                data.advanceToNextDay();
                int newDay = data.getCurrentDay();
                int requiredWork = data.getRequiredWorkCount();
                data.setHasSleep(true);

                // 同葬"无光之愿"buff在睡觉进入下一天后消失
                if (player.hasEffect(com.wzz.lobotocraft.init.ModEffects.WISH_WITHOUT_LIGHT.get())) {
                    player.removeEffect(com.wzz.lobotocraft.init.ModEffects.WISH_WITHOUT_LIGHT.get());
                }
                player.sendSystemMessage(Component.literal(
                        String.format("§6=== 第 %d 天 ===", newDay)
                ).withStyle(ChatFormatting.GOLD));

                player.sendSystemMessage(Component.literal(
                        String.format("§e今日需要完成 %d 次工作才能休息", requiredWork)
                ).withStyle(ChatFormatting.YELLOW));

                // 同步数据到客户端
                if (player instanceof ServerPlayer serverPlayer) {
                    if (!ItemUtil.hasItem(serverPlayer, ModItems.ARMOR_LOCK.get())) {
                        serverPlayer.inventory.add(new ItemStack(ModItems.ARMOR_LOCK.get()));
                    }
                    com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(
                            serverPlayer,
                            new com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket(
                                    newDay, 0, false, true
                            )
                    );
                }
            }
        });
    }
}