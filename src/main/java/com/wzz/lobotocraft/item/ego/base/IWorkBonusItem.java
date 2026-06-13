package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.world.entity.player.Player;

/**
 * 饰品工作加成接口
 * 为EGO饰品提供工作速度、成功率等加成效果
 */
public interface IWorkBonusItem {
    
    /**
     * 获取饰品的工作速度加成
     * @param player 佩戴玩家
     * @param workType 工作类型
     * @return 工作间隔减少的tick数（正数表示加速）
     */
    default int getWorkSpeedBonus(Player player, WorkType workType) {
        return 0;
    }
    
    /**
     * 获取饰品的工作成功率加成
     * @param player 佩戴玩家
     * @param workType 工作类型
     * @return 成功率加成百分比（如0.05表示+5%）
     */
    default float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.0f;
    }
    
    /**
     * 获取饰品对特定异想体风险的加成
     * @param riskLevel 异想体风险等级（如ZAYIN、TETH等）
     * @return 针对该风险等级的额外成功率加成
     */
    default float getRiskSpecificBonus(String riskLevel) {
        return 0.0f;
    }
}