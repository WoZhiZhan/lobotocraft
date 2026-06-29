package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class PlayerTabListDayHandler {

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data ->
                event.setDisplayName(Component.literal(formatTabListName(player, data.getCurrentDay())))
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshAllTabListNames(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshPlayerTabListName(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshPlayerTabListName(player);
        }
    }

    public static void refreshPlayerTabListName(ServerPlayer player) {
        player.refreshTabListName();
    }

    private static void refreshAllTabListNames(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        server.getPlayerList().getPlayers().forEach(PlayerTabListDayHandler::refreshPlayerTabListName);
    }

    private static String formatTabListName(ServerPlayer player, int currentDay) {
        return player.getGameProfile().getName() + "(day" + currentDay + ")";
    }
}
