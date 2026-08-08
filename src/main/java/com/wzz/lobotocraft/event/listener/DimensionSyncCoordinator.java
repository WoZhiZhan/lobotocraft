package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Serializes mod state packets that are regenerated when a player enters a
 * dimension. Vanilla sends a respawn and menu snapshot first; sending custom
 * packets from EntityJoinLevelEvent would otherwise be interleaved with that
 * sequence.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class DimensionSyncCoordinator {
    private static final Map<MinecraftServer, Map<UUID, Long>> PENDING = new IdentityHashMap<>();

    private DimensionSyncCoordinator() {
    }

    public static void schedule(ServerPlayer player) {
        if (player == null || player.isRemoved() || player.getServer() == null) return;
        MinecraftServer server = player.getServer();
        PENDING.computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                .merge(player.getUUID(), (long) server.getTickCount() + 1, Math::min);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Map<UUID, Long> pending = PENDING.get(server);
        if (pending == null || pending.isEmpty()) return;

        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Long>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > now) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (player.isChangingDimension()) {
                entry.setValue(now + 1);
                continue;
            }

            CapabilityEventHandler.syncDailyToClient(player);
            EmployeeStatsApplier.syncAttributesToClient(player);
            EscapeTracker.getInstance().syncToPlayer(player);
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
    }
}
