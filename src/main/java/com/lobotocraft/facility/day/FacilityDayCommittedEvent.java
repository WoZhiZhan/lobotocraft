package com.lobotocraft.facility.day;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Posted exactly once after a facility day transaction has committed. */
public final class FacilityDayCommittedEvent extends Event {
    private final MinecraftServer server;
    private final int oldDay;
    private final int newDay;

    public FacilityDayCommittedEvent(MinecraftServer server, int oldDay, int newDay) {
        this.server = server;
        this.oldDay = oldDay;
        this.newDay = newDay;
    }

    public MinecraftServer getServer() { return server; }
    public int getOldDay() { return oldDay; }
    public int getNewDay() { return newDay; }
}
