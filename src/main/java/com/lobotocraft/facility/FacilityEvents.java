package com.lobotocraft.facility;

import com.lobotocraft.facility.day.DayTransitionService;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.event.definition.work.WorkPreExtractionEvent;
import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FacilityEvents {
    private FacilityEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPreExtraction(WorkPreExtractionEvent event) {
        FacilityManager.modifySuccessRate(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWorkComplete(WorkCompleteEvent event) {
        FacilityManager.onWorkCompleted(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)
                || player.level().dimension() != ModDimensions.LOBOTO_KEY) return;
        event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        serverPlayer.displayClientMessage(Component.literal("请通过能源栏上方的结束日按钮结束今天。")
                .withStyle(ChatFormatting.AQUA), false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FacilityManager.sync(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) FacilityManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DayTransitionService.clear(event.getServer());
    }
}
