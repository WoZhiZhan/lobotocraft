package com.wzz.lobotocraft.event.definition.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 装备锁定时触发（可取消）
 */
@Cancelable
public class CompanyArmorLockEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final int currentDay;
    private String cancelReason = "";

    public CompanyArmorLockEvent(ServerPlayer player, CompanyDailyData data, int currentDay) {
        this.player = player;
        this.data = data;
        this.currentDay = currentDay;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public int getCurrentDay() { return currentDay; }
    
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }
}