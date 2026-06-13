package com.wzz.lobotocraft.event.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 进入新的一天时触发（可取消）
 */
@Cancelable
public class CompanyDayAdvanceEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final int oldDay;
    private final int newDay;

    public CompanyDayAdvanceEvent(ServerPlayer player, CompanyDailyData data, int oldDay, int newDay) {
        this.player = player;
        this.data = data;
        this.oldDay = oldDay;
        this.newDay = newDay;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public int getOldDay() { return oldDay; }
    public int getNewDay() { return newDay; }
}