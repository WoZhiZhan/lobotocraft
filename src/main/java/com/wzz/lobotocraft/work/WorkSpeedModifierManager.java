package com.wzz.lobotocraft.work;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作速度修改器管理器
 */
public class WorkSpeedModifierManager {
    
    private static final Map<UUID, List<IWorkSpeedModifier>> playerModifiers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, IWorkSpeedModifier> globalModifiers = new LinkedHashMap<>();
    
    /**
     * 注册全局速度修改器（所有玩家生效）
     */
    public static void registerGlobalModifier(IWorkSpeedModifier modifier) {
        globalModifiers.put(modifier.getClass(), modifier);
        // 按优先级排序
        List<IWorkSpeedModifier> sorted = new ArrayList<>(globalModifiers.values());
        sorted.sort(Comparator.comparingInt(IWorkSpeedModifier::getPriority));
        globalModifiers.clear();
        for (IWorkSpeedModifier m : sorted) {
            globalModifiers.put(m.getClass(), m);
        }
    }
    
    /**
     * 为玩家添加临时速度修改器
     */
    public static void addPlayerModifier(ServerPlayer player, IWorkSpeedModifier modifier) {
        playerModifiers.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(modifier);
        // 按优先级排序
        List<IWorkSpeedModifier> list = playerModifiers.get(player.getUUID());
        list.sort(Comparator.comparingInt(IWorkSpeedModifier::getPriority));
    }
    
    /**
     * 移除玩家的速度修改器
     */
    public static void removePlayerModifier(ServerPlayer player, IWorkSpeedModifier modifier) {
        List<IWorkSpeedModifier> list = playerModifiers.get(player.getUUID());
        if (list != null) {
            list.remove(modifier);
            if (list.isEmpty()) {
                playerModifiers.remove(player.getUUID());
            }
        }
    }
    
    /**
     * 移除指定类型的速度修改器
     */
    public static void removePlayerModifierByClass(ServerPlayer player, Class<? extends IWorkSpeedModifier> clazz) {
        List<IWorkSpeedModifier> list = playerModifiers.get(player.getUUID());
        if (list != null) {
            list.removeIf(m -> m.getClass() == clazz);
            if (list.isEmpty()) {
                playerModifiers.remove(player.getUUID());
            }
        }
    }
    
    /**
     * 清除玩家的所有速度修改器
     */
    public static void clearPlayerModifiers(ServerPlayer player) {
        playerModifiers.remove(player.getUUID());
    }
    
    /**
     * 计算玩家的最终速度倍率
     */
    public static float calculateFinalMultiplier(ServerPlayer player, WorkManager.WorkSession session) {
        float multiplier = 1.0f;
        
        // 应用全局修改器
        for (IWorkSpeedModifier modifier : globalModifiers.values()) {
            if (modifier.isActive(player, session)) {
                multiplier *= modifier.getSpeedMultiplier(player, session);
            }
        }
        
        // 应用玩家专属修改器
        List<IWorkSpeedModifier> modifiers = playerModifiers.get(player.getUUID());
        if (modifiers != null) {
            for (IWorkSpeedModifier modifier : modifiers) {
                if (modifier.isActive(player, session)) {
                    multiplier *= modifier.getSpeedMultiplier(player, session);
                }
            }
        }
        
        // 限制倍率范围（防止太快或太慢）
        return Math.max(0.2f, Math.min(10.0f, multiplier));
    }
}