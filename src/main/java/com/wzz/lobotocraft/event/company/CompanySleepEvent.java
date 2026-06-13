package com.wzz.lobotocraft.event.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 玩家睡觉时触发（用于结束一天）（可取消）
 */
@Cancelable
public class CompanySleepEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final int currentDay;
    private final int todayWorkCount;
    private final int requiredWorkCount;
    private boolean canSleep = true;
    private String cancelReason = "";

    public CompanySleepEvent(ServerPlayer player, CompanyDailyData data, 
                              int currentDay, int todayWorkCount, int requiredWorkCount) {
        this.player = player;
        this.data = data;
        this.currentDay = currentDay;
        this.todayWorkCount = todayWorkCount;
        this.requiredWorkCount = requiredWorkCount;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public int getCurrentDay() { return currentDay; }
    public int getTodayWorkCount() { return todayWorkCount; }
    public int getRequiredWorkCount() { return requiredWorkCount; }
    public boolean hasCompletedWork() { return todayWorkCount >= requiredWorkCount; }
    public int getRemainingWork() { return Math.max(0, requiredWorkCount - todayWorkCount); }
    
    public boolean canSleep() { return canSleep; }
    public void setCanSleep(boolean canSleep) { this.canSleep = canSleep; }
    
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }
}