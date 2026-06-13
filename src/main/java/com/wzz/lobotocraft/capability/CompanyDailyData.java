package com.wzz.lobotocraft.capability;

import com.wzz.lobotocraft.event.company.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * 公司日常数据
 * 存储玩家的工作进度、天数、装备锁定状态
 */
public class CompanyDailyData {

    // 当前天数（从1开始）
    private int currentDay = 1;

    // 今日已完成工作次数
    private int todayWorkCount = 0;

    // 装备是否已锁定
    private boolean armorLocked = false;

    private boolean hasSleep = false;

    // 存储原始抗性值（用于装备解锁时恢复）
    private double originalRedResistance = 1.0;
    private double originalWhiteResistance = 1.0;
    private double originalBlackResistance = 1.5;
    private double originalBlueResistance = 2.0;

    // 标记是否已应用装备抗性
    private boolean resistancesApplied = false;
    
    // 关联的玩家（用于事件触发）
    private transient ServerPlayer owner;

    public CompanyDailyData() {
    }
    
    /**
     * 设置拥有者玩家
     */
    public void setOwner(ServerPlayer player) {
        this.owner = player;
    }
    
    /**
     * 获取拥有者玩家
     */
    public ServerPlayer getOwner() {
        return owner;
    }

    /**
     * 获取今日需要的工作次数
     * 第一天2次，之后每天+2
     */
    public int getRequiredWorkCount() {
        return currentDay * 2;
    }

    /**
     * 检查今日工作是否完成
     */
    public boolean hasCompletedTodayWork() {
        return todayWorkCount >= getRequiredWorkCount();
    }

    /**
     * 增加今日工作次数
     */
    public void addWorkCount() {
        todayWorkCount++;
        
        // 触发工作完成事件
        if (owner != null) {
            MinecraftForge.EVENT_BUS.post(new CompanyWorkCompleteEvent(
                    owner, this, todayWorkCount, getRequiredWorkCount()
            ));
        }
    }

    /**
     * 进入新的一天
     * 重置工作次数，天数+1，清除装备锁定
     */
    public void advanceToNextDay() {
        int oldDay = currentDay;
        int newDay = currentDay + 1;
        
        // 触发进入新的一天事件
        if (owner != null) {
            CompanyDayAdvanceEvent event = new CompanyDayAdvanceEvent(owner, this, oldDay, newDay);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                // 事件被取消
                return;
            }
        }
        
        currentDay = newDay;
        todayWorkCount = 0;
        armorLocked = false;
        resistancesApplied = false;  // 重置抗性应用状态
    }

    /**
     * 锁定装备
     */
    public void lockArmor() {
        // 触发装备锁定事件
        if (owner != null) {
            CompanyArmorLockEvent event = new CompanyArmorLockEvent(owner, this, currentDay);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                // 事件被取消
                return;
            }
        }
        this.armorLocked = true;
    }

    /**
     * 解锁装备（用于测试或特殊情况）
     */
    public void unlockArmor() {
        // 触发装备解锁事件
        if (owner != null) {
            MinecraftForge.EVENT_BUS.post(new CompanyArmorUnlockEvent(owner, this, currentDay));
        }
        this.armorLocked = false;
        this.resistancesApplied = false;  // 解锁时重置抗性应用状态
    }

    /**
     * 检查装备是否已锁定
     */
    public boolean isArmorLocked() {
        return armorLocked;
    }

    // Getters
    public int getCurrentDay() {
        return currentDay;
    }

    public int getTodayWorkCount() {
        return todayWorkCount;
    }

    /**
     * 获取工作进度百分比
     */
    public float getWorkProgress() {
        return (float) todayWorkCount / getRequiredWorkCount();
    }

    /**
     * 获取进度显示文本
     */
    public String getProgressText() {
        return todayWorkCount + "/" + getRequiredWorkCount();
    }

    public void setCurrentDay(int day) {
        this.currentDay = Math.max(1, day);
    }

    public void setTodayWorkCount(int count) {
        this.todayWorkCount = Math.max(0, count);
    }

    public void setHasSleep(boolean hasSleep) {
        // 触发睡觉事件
        if (owner != null && hasSleep && !this.hasSleep) {
            CompanySleepEvent event = new CompanySleepEvent(
                    owner, this, currentDay, todayWorkCount, getRequiredWorkCount()
            );
            if (MinecraftForge.EVENT_BUS.post(event)) {
                // 事件被取消，不允许睡觉
                owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c" + event.getCancelReason()
                ));
                return;
            }
            
            // 如果工作已完成，进入下一天
            if (event.hasCompletedWork()) {
                advanceToNextDay();
            }
        }
        this.hasSleep = hasSleep;
    }

    public boolean isHasSleep() {
        return hasSleep;
    }

    // ==================== 抗性管理方法 ====================

    /**
     * 保存原始抗性值
     */
    public void saveOriginalResistances(double red, double white, double black, double blue) {
        this.originalRedResistance = red;
        this.originalWhiteResistance = white;
        this.originalBlackResistance = black;
        this.originalBlueResistance = blue;
    }

    /**
     * 获取原始红色抗性
     */
    public double getOriginalRedResistance() {
        return originalRedResistance;
    }

    /**
     * 获取原始白色抗性
     */
    public double getOriginalWhiteResistance() {
        return originalWhiteResistance;
    }

    /**
     * 获取原始黑色抗性
     */
    public double getOriginalBlackResistance() {
        return originalBlackResistance;
    }

    /**
     * 获取原始蓝色抗性
     */
    public double getOriginalBlueResistance() {
        return originalBlueResistance;
    }

    /**
     * 设置抗性已应用标记
     */
    public void setResistancesApplied(boolean applied) {
        this.resistancesApplied = applied;
    }

    /**
     * 检查抗性是否已应用
     */
    public boolean isResistancesApplied() {
        return resistancesApplied;
    }
    
    /**
     * 应用装备抗性（带事件）
     */
    public boolean applyResistances(double red, double white, double black, double blue) {
        if (owner != null) {
            CompanyResistanceApplyEvent event = new CompanyResistanceApplyEvent(
                    owner, this, red, white, black, blue
            );
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return false;
            }
        }
        
        saveOriginalResistances(red, white, black, blue);
        resistancesApplied = true;
        return true;
    }

    // ==================== NBT序列化 ====================

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CurrentDay", currentDay);
        tag.putInt("TodayWorkCount", todayWorkCount);
        tag.putBoolean("ArmorLocked", armorLocked);
        tag.putBoolean("HasSleep", hasSleep);

        // 保存原始抗性值
        tag.putDouble("OriginalRedResistance", originalRedResistance);
        tag.putDouble("OriginalWhiteResistance", originalWhiteResistance);
        tag.putDouble("OriginalBlackResistance", originalBlackResistance);
        tag.putDouble("OriginalBlueResistance", originalBlueResistance);
        tag.putBoolean("ResistancesApplied", resistancesApplied);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        currentDay = tag.getInt("CurrentDay");
        if (currentDay < 1) currentDay = 1;

        todayWorkCount = tag.getInt("TodayWorkCount");
        armorLocked = tag.getBoolean("ArmorLocked");
        hasSleep = tag.getBoolean("HasSleep");

        // 加载原始抗性值
        if (tag.contains("OriginalRedResistance")) {
            originalRedResistance = tag.getDouble("OriginalRedResistance");
        }
        if (tag.contains("OriginalWhiteResistance")) {
            originalWhiteResistance = tag.getDouble("OriginalWhiteResistance");
        }
        if (tag.contains("OriginalBlackResistance")) {
            originalBlackResistance = tag.getDouble("OriginalBlackResistance");
        }
        if (tag.contains("OriginalBlueResistance")) {
            originalBlueResistance = tag.getDouble("OriginalBlueResistance");
        }
        if (tag.contains("ResistancesApplied")) {
            resistancesApplied = tag.getBoolean("ResistancesApplied");
        }
    }

    /**
     * 复制数据（用于同步）
     */
    public void copyFrom(CompanyDailyData source) {
        this.currentDay = source.currentDay;
        this.todayWorkCount = source.todayWorkCount;
        this.armorLocked = source.armorLocked;
        this.hasSleep = source.hasSleep;
        this.originalRedResistance = source.originalRedResistance;
        this.originalWhiteResistance = source.originalWhiteResistance;
        this.originalBlackResistance = source.originalBlackResistance;
        this.originalBlueResistance = source.originalBlueResistance;
        this.resistancesApplied = source.resistancesApplied;
        this.owner = source.owner;
    }
}