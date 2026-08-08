package com.wzz.lobotocraft.event.listener;

import com.lobotocraft.facility.day.PlayerEnteredFacilityDayEvent;
import com.wzz.lobotocraft.entity.abnormality.EntityWingBeat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative lifecycle for fairy-feast sessions. */
@Mod.EventBusSubscriber
public final class WingBeatSessionEvents {
    private WingBeatSessionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 5 == 0) {
            EntityWingBeat.expireSessionIfNeeded(player);
        }
    }

    @SubscribeEvent
    public static void onDayAdvance(PlayerEnteredFacilityDayEvent event) {
        EntityWingBeat.clearSession(event.getPlayer());
    }
}
