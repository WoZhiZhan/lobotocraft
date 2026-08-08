package com.lobotocraft.facility.day;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** Posted once for every eligible human player after a committed facility day. */
public final class PlayerEnteredFacilityDayEvent extends Event {
    private final ServerPlayer player;
    private final int oldDay;
    private final int newDay;

    public PlayerEnteredFacilityDayEvent(ServerPlayer player, int oldDay, int newDay) {
        this.player = player;
        this.oldDay = oldDay;
        this.newDay = newDay;
    }

    public ServerPlayer getPlayer() { return player; }
    public int getOldDay() { return oldDay; }
    public int getNewDay() { return newDay; }
}
