package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Company beds no longer advance the facility day; the energy vote owns that transition. */
@Mod.EventBusSubscriber
public final class CompanySleepHandler {
    private CompanySleepHandler() {
    }

    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.level().dimension() == ModDimensions.LOBOTO_KEY) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @SubscribeEvent
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.level().dimension() == ModDimensions.LOBOTO_KEY) {
            event.setResult(Event.Result.DENY);
        }
    }
}
