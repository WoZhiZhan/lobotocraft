package com.wzz.lobotocraft.event.definition.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 装备解锁时触发
 */
public class CompanyArmorUnlockEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final int currentDay;

    public CompanyArmorUnlockEvent(ServerPlayer player, CompanyDailyData data, int currentDay) {
        this.player = player;
        this.data = data;
        this.currentDay = currentDay;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public int getCurrentDay() { return currentDay; }
}