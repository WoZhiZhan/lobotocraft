package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FacilityKnowledgeSyncPacket;
import java.util.LinkedHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class FacilityKnowledgeService {
    private FacilityKnowledgeService() {}

    /** Import legacy personal knowledge before checking a purchase, then share the union. */
    public static void synchronize(ServerPlayer requester) {
        synchronize(requester.server, requester, true);
    }

    public static void unlock(ServerPlayer purchaser, String code, int index) {
        FacilityKnowledgeData.get(purchaser.server).unlock(code, index);
        synchronize(purchaser.server, purchaser, true);
    }

    private static void synchronize(MinecraftServer server, ServerPlayer extra, boolean forceExtra) {
        var players = new LinkedHashSet<ServerPlayer>(server.getPlayerList().getPlayers());
        for (var employee : EmployeeRuntimeManager.activePlayers()) {
            if (employee.server == server) players.add(employee);
        }
        if (extra != null) players.add(extra);
        FacilityKnowledgeData shared = FacilityKnowledgeData.get(server);
        // Import everyone before applying, so the result does not depend on player iteration order.
        for (var player : players) player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .ifPresent(shared::merge);
        for (var player : players) player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .ifPresent(personal -> {
                    boolean changed = shared.applyTo(personal);
                    if (!(player instanceof EmployeeServerPlayer) && (changed || forceExtra && player == extra)) {
                        MessageLoader.getLoader().sendToPlayer(player,
                                new FacilityKnowledgeSyncPacket(personal.knowledgeSnapshot()));
                        for (var entity : player.serverLevel().getAllEntities()) {
                            if (entity instanceof AbstractAbnormality abnormality
                                    && personal.getObservationLevel(abnormality.getAbnormalityCode()) > 0
                                    && player.distanceToSqr(abnormality) <= 128D * 128D) {
                                abnormality.sendPerPlayerName(player, abnormality.getLocalizedAbnormalityName());
                            }
                        }
                    }
                });
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            synchronize(event.getServer(), null, false);
        }
    }
}
