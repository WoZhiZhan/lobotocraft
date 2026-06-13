package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.item.linkage.StrangeBadgeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 奇怪的工牌客户端追踪器
 * 追踪鼠标悬停在物品上的时间
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class StrangeBadgeClientTracker {
    
    private static long hoverStartTime = 0;
    private static boolean wasHovering = false;
    
    /**
     * 客户端tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) {
            resetHover();
            return;
        }
        
        // 检查鼠标是否悬停在奇怪的工牌上
        boolean isHovering = isHoveringStrangeBadge(mc.player);
        
        if (isHovering && !wasHovering) {
            // 开始悬停
            hoverStartTime = System.currentTimeMillis();
            wasHovering = true;
        } else if (!isHovering && wasHovering) {
            // 停止悬停
            resetHover();
        }
    }
    
    /**
     * 检查是否悬停在奇怪的工牌上
     */
    private static boolean isHoveringStrangeBadge(Player player) {
        // 检查主手
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof StrangeBadgeItem) {
            // 检查玩家是否恐慌
            final boolean[] isPanic = {false};
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                isPanic[0] = mental.getMentalValue() <= 0;
            });
            return isPanic[0];
        }
        
        // 检查副手
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof StrangeBadgeItem) {
            final boolean[] isPanic = {false};
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                isPanic[0] = mental.getMentalValue() <= 0;
            });
            return isPanic[0];
        }
        
        return false;
    }
    
    /**
     * 重置悬停状态
     */
    private static void resetHover() {
        hoverStartTime = 0;
        wasHovering = false;
    }
    
    /**
     * 获取悬停时长（毫秒）
     */
    public static long getHoverDuration() {
        if (!wasHovering) return 0;
        return System.currentTimeMillis() - hoverStartTime;
    }
    
    /**
     * 获取悬停进度（0.0-1.0）
     */
    public static float getHoverProgress() {
        long duration = getHoverDuration();
        return Math.min(1.0f, duration / 6000.0f); // 6秒
    }
}