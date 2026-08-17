package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

import java.util.Map;
import java.util.WeakHashMap;

final class GeburahBossBars {
    private static final Map<MinecraftServer, Bars> ACTIVE = new WeakHashMap<>();

    private GeburahBossBars() {
    }

    static void start(MinecraftServer server, GeburahEncounterData data) {
        finish(server);
        Bars bars = new Bars();
        ACTIVE.put(server, bars);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (data.isParticipant(player.getUUID())) bars.add(player);
        }
    }

    static void addPlayer(ServerPlayer player, GeburahEncounterData data) {
        Bars bars = ACTIVE.get(player.getServer());
        if (bars != null && (data.isParticipant(player.getUUID()) || data.isObserver(player.getUUID()))) {
            bars.add(player);
        }
    }

    static void update(MinecraftServer server, EntityGeburah geburah) {
        Bars bars = ACTIVE.get(server);
        if (bars == null) return;
        GeburahCombatState state = geburah.combatState();
        bars.health.setProgress(clamp(state.health() / state.maxHealth()));
        bars.posture.setProgress(clamp(state.posture() / GeburahCombatState.MAX_POSTURE));
        float egoProgress = geburah.isPhaseTransitioning()
                ? 0.0F : state.ego() / GeburahCombatState.MAX_EGO;
        bars.ego.setProgress(clamp(egoProgress));
    }

    static void finish(MinecraftServer server) {
        Bars bars = ACTIVE.remove(server);
        if (bars != null) bars.removeAll();
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static final class Bars {
        private final ServerBossEvent health = new ServerBossEvent(
                Component.translatable("bossbar.lobotocraft.geburah.health"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        private final ServerBossEvent posture = new ServerBossEvent(
                Component.translatable("bossbar.lobotocraft.geburah.posture"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        private final ServerBossEvent ego = new ServerBossEvent(
                Component.translatable("bossbar.lobotocraft.geburah.ego"),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

        private void add(ServerPlayer player) {
            health.addPlayer(player);
            posture.addPlayer(player);
            ego.addPlayer(player);
        }

        private void removeAll() {
            health.removeAllPlayers();
            posture.removeAllPlayers();
            ego.removeAllPlayers();
        }
    }
}
