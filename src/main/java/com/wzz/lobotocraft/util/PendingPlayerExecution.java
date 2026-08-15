package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.ModMain;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Carries unavoidable delayed executions across a disconnect/reconnect. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class PendingPlayerExecution {
    private static final String PENDING_TAG = "LobotocraftPendingExecution";
    private static final Set<UUID> RUNTIME_PENDING = ConcurrentHashMap.newKeySet();

    private PendingPlayerExecution() {
    }

    public static void mark(ServerPlayer player) {
        if (player == null) return;
        RUNTIME_PENDING.add(player.getUUID());
        player.getPersistentData().putBoolean(PENDING_TAG, true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean pending = RUNTIME_PENDING.remove(player.getUUID())
                | player.getPersistentData().getBoolean(PENDING_TAG);
        player.getPersistentData().remove(PENDING_TAG);
        if (!pending || !player.isAlive()) return;
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.pending_execution.resolved"));
        player.kill();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNTIME_PENDING.clear();
    }
}
