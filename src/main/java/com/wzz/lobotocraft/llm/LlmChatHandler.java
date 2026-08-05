package com.wzz.lobotocraft.llm;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class LlmChatHandler {
    private LlmChatHandler() {
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String prefix = LlmConfig.CHAT_PREFIX;
        if (!event.getRawText().startsWith(prefix)) return;
        event.setCanceled(true);
        String command = event.getRawText().substring(prefix.length()).trim();
        if (!command.isEmpty()) LlmDispatchService.submit(event.getPlayer(), command);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LlmDispatchService.clearPlayer(player.getUUID());
        EmployeeRadioService.clearPlayer(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LlmConfig.initializeServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LlmDispatchService.clearAll();
        EmployeeRadioService.clearAll();
        LlmConfig.clearSessionKey();
    }
}
