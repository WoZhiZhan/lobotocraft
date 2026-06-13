package com.wzz.lobotocraft.event.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 完成一次工作时触发
 */
public class CompanyWorkCompleteEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final int currentWorkCount;
    private final int requiredWorkCount;
    private final boolean isDayComplete;

    public CompanyWorkCompleteEvent(ServerPlayer player, CompanyDailyData data, 
                                     int currentWorkCount, int requiredWorkCount) {
        this.player = player;
        this.data = data;
        this.currentWorkCount = currentWorkCount;
        this.requiredWorkCount = requiredWorkCount;
        this.isDayComplete = currentWorkCount >= requiredWorkCount;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public int getCurrentWorkCount() { return currentWorkCount; }
    public int getRequiredWorkCount() { return requiredWorkCount; }
    public boolean isDayComplete() { return isDayComplete; }
    public int getRemainingWork() { return Math.max(0, requiredWorkCount - currentWorkCount); }
    public float getProgress() { return (float) currentWorkCount / requiredWorkCount; }
}