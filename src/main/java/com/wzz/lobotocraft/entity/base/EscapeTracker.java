package com.wzz.lobotocraft.entity.base;

import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.EscapeAlertPacket;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative facility emergency controller.
 *
 * <p>Despite the legacy class name, this now follows the original game's
 * score model shared by escaped abnormalities and ordeals. Score persists
 * after a threat ends and falls naturally after the recovery delay.</p>
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class EscapeTracker {
    private static final EscapeTracker INSTANCE = new EscapeTracker();

    private final Map<MinecraftServer, EmergencyState> states = new HashMap<>();

    public static EscapeTracker getInstance() {
        return INSTANCE;
    }

    private EscapeTracker() {
    }

    public synchronized void onEscapeStart(AbstractAbnormality entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        EmergencyState state = state(level.getServer());
        RiskLevel previous = state.escapes.put(entity.getUUID(), entity.getRiskLevel());
        if (previous == null) addRiskScore(level.getServer(), entity.getRiskLevel());
    }

    public synchronized void onEscapeStop(AbstractAbnormality entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        onEscapeStop(level.getServer(), entity.getUUID());
    }

    /** Removes exactly one escaped entity. Repeated lifecycle callbacks are harmless. */
    public synchronized void onEscapeStop(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return;
        removeExact(state(server).escapes, entityId);
    }

    /** Adds the original risk-scaled score after an ordeal successfully starts. */
    public synchronized void onOrdealStart(ServerLevel level, RiskLevel risk) {
        if (level == null) return;
        addRiskScore(level.getServer(), risk);
    }

    public synchronized void onOrdealStart(ServerLevel level, OrdealTier tier) {
        if (level == null || tier == null || tier == OrdealTier.NONE) return;
        addRiskScore(level.getServer(), EmergencyAlarmPolicy.ordealRisk(tier));
    }

    static boolean removeExact(Map<UUID, RiskLevel> escapes, UUID entityId) {
        return escapes != null && entityId != null && escapes.remove(entityId) != null;
    }

    public synchronized int getCurrentAlertLevel(MinecraftServer server) {
        return EmergencyAlarmPolicy.alertLevel(state(server).score);
    }

    synchronized float getCurrentScore(MinecraftServer server) {
        return state(server).score;
    }

    static int calculateAlertLevel(Map<UUID, RiskLevel> escapes) {
        return calculateAlertLevel(escapes, 1);
    }

    static int calculateAlertLevel(Map<UUID, RiskLevel> escapes, int openedDepartments) {
        return EmergencyAlarmPolicy.alertLevel(calculateScore(escapes, openedDepartments));
    }

    static float calculateScore(Map<UUID, RiskLevel> escapes, int openedDepartments) {
        if (escapes == null || escapes.isEmpty()) return 0.0F;
        float score = 0.0F;
        for (RiskLevel risk : escapes.values()) {
            score = EmergencyAlarmPolicy.add(score,
                    EmergencyAlarmPolicy.eventScore(risk, openedDepartments));
        }
        return score;
    }

    /** Sends the authoritative current state to a joining or respawning player. */
    public synchronized void syncToPlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        MessageLoader.getLoader().sendToPlayer(player,
                new EscapeAlertPacket(getCurrentAlertLevel(player.getServer())));
    }

    /** Re-broadcasts the authoritative level after a global client state transition. */
    public synchronized void syncAll(MinecraftServer server) {
        if (server == null) return;
        broadcastLevel(server, getCurrentAlertLevel(server));
    }

    /** A new management day always starts at the original NORMAL emergency state. */
    public synchronized void resetForDay(MinecraftServer server) {
        if (server == null) return;
        EmergencyState state = state(server);
        state.escapes.clear();
        state.score = 0.0F;
        state.recoveryBlockedUntil = 0L;
        state.nextRecoveryTick = 0L;
        state.maxEmployeeCount = liveEmployeeCount(server);
        state.lastAlertLevel = -1;
        broadcastLevel(server, -1);
    }

    private void addRiskScore(MinecraftServer server, RiskLevel risk) {
        float amount = EmergencyAlarmPolicy.eventScore(risk, openedDepartmentCount(server));
        if (amount <= 0.0F) return;
        EmergencyState state = state(server);
        state.score = EmergencyAlarmPolicy.add(state.score, amount);
        long now = server.overworld().getGameTime();
        state.recoveryBlockedUntil = now + EmergencyAlarmPolicy.RECOVERY_BLOCK_TICKS;
        state.nextRecoveryTick = state.recoveryBlockedUntil
                + EmergencyAlarmPolicy.RECOVERY_INTERVAL_TICKS;
        updateWorkforcePeak(server, state);
        updateAlertLevel(server, state);
    }

    private static int openedDepartmentCount(MinecraftServer server) {
        DepartmentProgressData progress = DepartmentProgressData.get(server);
        int count = 0;
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department != EmployeeDepartment.UNASSIGNED && progress.isOpen(department)) count++;
        }
        return Math.max(1, count);
    }

    private static int liveEmployeeCount(MinecraftServer server) {
        Set<UUID> alive = new HashSet<>();
        EmployeeRuntimeManager.activePlayers().stream()
                .filter(player -> player.isAlive() && !player.isRemoved())
                .forEach(player -> alive.add(player.getUUID()));
        server.getPlayerList().getPlayers().stream()
                .filter(player -> player.isAlive() && !player.isRemoved())
                .forEach(player -> alive.add(player.getUUID()));
        return alive.size();
    }

    private static void updateWorkforcePeak(MinecraftServer server, EmergencyState state) {
        state.maxEmployeeCount = Math.max(state.maxEmployeeCount, liveEmployeeCount(server));
    }

    private static float aliveRatio(MinecraftServer server, EmergencyState state) {
        int alive = liveEmployeeCount(server);
        if (state.maxEmployeeCount <= 0 || server.getPlayerList().getPlayerCount() == 0) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, alive / (float) state.maxEmployeeCount));
    }

    private EmergencyState state(MinecraftServer server) {
        return states.computeIfAbsent(server, ignored -> new EmergencyState());
    }

    private void updateAlertLevel(MinecraftServer server, EmergencyState state) {
        int level = EmergencyAlarmPolicy.alertLevel(state.score);
        if (level == state.lastAlertLevel) return;
        state.lastAlertLevel = level;
        broadcastLevel(server, level);
    }

    private static void broadcastLevel(MinecraftServer server, int level) {
        EscapeAlertPacket packet = new EscapeAlertPacket(level);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(player, packet);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        getInstance().tick(event.getServer());
    }

    private synchronized void tick(MinecraftServer server) {
        EmergencyState state = states.get(server);
        if (state == null || state.score <= 0.0F) return;
        updateWorkforcePeak(server, state);
        long now = server.overworld().getGameTime();
        if (now < state.recoveryBlockedUntil || now < state.nextRecoveryTick) return;
        state.nextRecoveryTick = now + EmergencyAlarmPolicy.RECOVERY_INTERVAL_TICKS;
        state.score = EmergencyAlarmPolicy.recover(state.score, aliveRatio(server, state));
        updateAlertLevel(server, state);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        getInstance().rebuild(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (getInstance()) {
            getInstance().states.remove(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof AbstractAbnormality abnormality)
                || !abnormality.hasEscape()
                || !(event.getLevel() instanceof ServerLevel level)) return;
        synchronized (getInstance()) {
            EmergencyState state = getInstance().state(level.getServer());
            RiskLevel previous = state.escapes.put(abnormality.getUUID(), abnormality.getRiskLevel());
            if (previous == null) getInstance().addRiskScore(level.getServer(), abnormality.getRiskLevel());
        }
    }

    private synchronized void rebuild(MinecraftServer server) {
        EmergencyState state = new EmergencyState();
        states.put(server, state);
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof AbstractAbnormality abnormality && abnormality.hasEscape()) {
                    state.escapes.put(abnormality.getUUID(), abnormality.getRiskLevel());
                }
            }
        }
        int openedDepartments = openedDepartmentCount(server);
        state.score = calculateScore(state.escapes, openedDepartments);
        OrdealData ordeal = OrdealData.get(server.overworld());
        if (ordeal.hasActiveDawn()) {
            state.score = EmergencyAlarmPolicy.add(state.score,
                    EmergencyAlarmPolicy.eventScore(RiskLevel.TETH, openedDepartments));
        }
        if (ordeal.hasActiveMidday()) {
            state.score = EmergencyAlarmPolicy.add(state.score,
                    EmergencyAlarmPolicy.eventScore(RiskLevel.HE, openedDepartments));
        }
        if (ordeal.hasActiveLateOrdeal()) {
            OrdealTier tier = ordeal.getLateOrdealTier() == 1 ? OrdealTier.DUSK
                    : ordeal.getLateOrdealTier() == 2 ? OrdealTier.MIDNIGHT : OrdealTier.NONE;
            state.score = EmergencyAlarmPolicy.add(state.score,
                    EmergencyAlarmPolicy.eventScore(EmergencyAlarmPolicy.ordealRisk(tier),
                            openedDepartments));
        }
        long now = server.overworld().getGameTime();
        state.recoveryBlockedUntil = now + EmergencyAlarmPolicy.RECOVERY_BLOCK_TICKS;
        state.nextRecoveryTick = state.recoveryBlockedUntil
                + EmergencyAlarmPolicy.RECOVERY_INTERVAL_TICKS;
        state.maxEmployeeCount = liveEmployeeCount(server);
        state.lastAlertLevel = EmergencyAlarmPolicy.alertLevel(state.score);
    }

    private static final class EmergencyState {
        private final Map<UUID, RiskLevel> escapes = new HashMap<>();
        private float score;
        private long recoveryBlockedUntil;
        private long nextRecoveryTick;
        private int maxEmployeeCount;
        private int lastAlertLevel = -1;
    }
}
