package com.lobotocraft.facility.api;

import com.lobotocraft.facility.data.FacilityDayData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public interface DayCompletionRequirement {
    String id();

    boolean isSatisfied(ServerPlayer player, FacilityDayData data);

    Component unmetMessage(ServerPlayer player, FacilityDayData data);
}
