package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import net.minecraft.world.entity.player.Player;

/**
 * 装备效果检查工具
 * 用于判断玩家的装备是否已锁定并生效
 * 使用方法：
 * 在计算装备抗性、套装效果等地方调用 shouldApplyArmorEffects(player)
 * 如果返回false，则不应用装备效果，使用基础抗性（通常是1.0）
 */
public class ArmorEffectChecker {

    /**
     * 检查玩家的装备是否已锁定
     * 
     * @param player 玩家
     * @return true如果装备已锁定，false如果未锁定
     */
    public static boolean isArmorLocked(Player player) {
        final boolean[] locked = {false};
        
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            locked[0] = data.isArmorLocked();
        });
        
        return locked[0];
    }

    /**
     * 检查装备效果是否应该生效
     * 使用场景：
     * - 计算伤害减免时
     * - 检查套装效果时
     * - 应用装备属性时
     * 
     * @param player 玩家
     * @return true如果装备效果应该生效，false如果不生效
     */
    public static boolean shouldApplyArmorEffects(Player player) {
        // 创造模式和旁观模式的玩家装备始终生效
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }

        // 检查是否已锁定
        return isArmorLocked(player);
    }

    /**
     * 检查套装效果是否应该生效
     * 
     * @param player 玩家
     * @return true如果套装效果应该生效
     */
    public static boolean shouldApplySetBonus(Player player) {
        return shouldApplyArmorEffects(player);
    }
}