package com.wzz.lobotocraft.work;

import net.minecraft.server.level.ServerPlayer;

/**
 * 工作速度修改器接口
 */
public interface IWorkSpeedModifier {
    
    /**
     * 获取速度修改倍率（1.0 = 正常速度，2.0 = 两倍速，0.5 = 半速）
     * @param player 玩家
     * @param session 工作会话
     * @return 速度倍率
     */
    float getSpeedMultiplier(ServerPlayer player, WorkManager.WorkSession session);
    
    /**
     * 修改器优先级（数字越小越先应用，用于处理冲突）
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 是否应该生效
     */
    default boolean isActive(ServerPlayer player, WorkManager.WorkSession session) {
        return true;
    }
}