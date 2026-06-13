package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.world.entity.player.Player;

/**
 * 影响精神值的物品接口
 */
public interface IMentalValueItem {
    
    /**
     * 是否有精神值加成
     */
    boolean hasMentalBonus();
    
    /**
     * 获取精神值加成
     * @param player 玩家
     * @return 精神值加成值
     */
    default float getMentalBonus(Player player) {
        return 0.0f;
    }
    
    /**
     * 获取精神值恢复速度（每tick）
     * @param player 玩家
     * @return 每tick恢复的精神值
     */
    default float getMentalRegenPerTick(Player player) {
        return 0.0f;
    }
    
    /**
     * 获取精神值恢复速度（每秒）
     * 便捷方法，1秒 = 20 ticks
     * @param player 玩家
     * @return 每秒恢复的精神值
     */
    default float getMentalRegenPerSecond(Player player) {
        return getMentalRegenPerTick(player) * 20.0f;
    }
}