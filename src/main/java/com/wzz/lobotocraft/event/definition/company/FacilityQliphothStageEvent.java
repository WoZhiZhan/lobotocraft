package com.wzz.lobotocraft.event.definition.company;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/** Fired once when the facility advances a Qliphoth alarm stage. */
public final class FacilityQliphothStageEvent extends Event {
    private final ServerLevel level;
    private final int triggeredLevel;

    public FacilityQliphothStageEvent(ServerLevel level, int triggeredLevel) {
        this.level = level;
        this.triggeredLevel = Math.max(1, triggeredLevel);
    }

    public ServerLevel getLevel() {
        return level;
    }

    public int getTriggeredLevel() {
        return triggeredLevel;
    }
}
