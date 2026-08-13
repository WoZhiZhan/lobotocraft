package com.wzz.lobotocraft.exhibition;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.command.ExhibitionCommand;
import com.wzz.lobotocraft.command.KillCommand;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBird;
import com.wzz.lobotocraft.entity.abnormality.EntityHappyTeddy;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.listener.MentalValueEventHandler;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import com.wzz.lobotocraft.event.listener.TeddyExecutionManager;
import com.wzz.lobotocraft.util.BuffUtil;
import com.wzz.lobotocraft.util.TimerEntry;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.world.data.ExhibitionModeData;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side public-demo safety controls and one-click recovery. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class ExhibitionSafetyService {
    private ExhibitionSafetyService() {
    }

    public static boolean isEnabled(MinecraftServer server) {
        return server != null && ExhibitionModeData.get(server).isEnabled();
    }

    public static void setEnabled(MinecraftServer server, boolean enabled) {
        if (server != null) ExhibitionModeData.get(server).setEnabled(enabled);
    }

    public static RecoveryReport recover(MinecraftServer server) {
        WorkStopReport stopped = stopAllWork(server);
        int recoveredPlayers = clearAllControlStates(server);
        int returnedAbnormalities = returnAllAbnormalities(server);
        int resetEmployees = resetAllAiNavigation(server);
        EventCleanupReport cleaned = cleanupEventEntities(server);
        int clearedMeltdowns = FacilityDayData.get(server.overworld()).clearActiveMeltdowns();
        EscapeTracker.getInstance().resetForDay(server);
        FacilityManager.syncAll(server, FacilityDayData.get(server.overworld()));
        ExhibitionModeData.get(server).recordRecovery();
        server.saveEverything(false, true, true);

        return new RecoveryReport(recoveredPlayers, stopped.works(), stopped.orders(),
                returnedAbnormalities, cleaned.ordealTargets(), clearedMeltdowns,
                cleaned.executions(), resetEmployees);
    }

    public static WorkStopReport stopAllWork(MinecraftServer server) {
        int canceledWorks = 0;
        int canceledOrders = 0;
        for (ServerPlayer player : participants(server).values()) {
            if (WorkManager.getWorkSession(player) != null) {
                WorkManager.cancelWork(player, "展会停止工作");
                canceledWorks++;
            }
            if (player instanceof EmployeeServerPlayer employee
                    && EmployeeRuntimeManager.cancelOrder(employee.employeeRecord(), "展会停止工作")) {
                canceledOrders++;
            }
        }
        return new WorkStopReport(canceledWorks, canceledOrders);
    }

    public static int clearAllControlStates(MinecraftServer server) {
        int recoveredPlayers = 0;
        for (ServerPlayer player : participants(server).values()) {
            TimerEntry.shutdown(player);
            TeddyExecutionManager.finish(player);
            PlayerControlLock.unlock(player);
            EntityRedShoes.clearControlForRecovery(player);
            EntityLargeBird.clearLargeBirdCharm(player);
            BuffUtil.removeKiss(player);
            BuffUtil.removeFriendshipProof(player);
            player.getPersistentData().remove("isSnowQueen");
            player.getPersistentData().remove("isSnowQueenDuel");
            player.getPersistentData().remove("isInWingBeat");
            player.getPersistentData().remove("lobotocraft_abnormality_executed");
            player.removeAllEffects();
            MentalValueEventHandler.forceRecover(player);
            if (player.isAlive()) player.setHealth(player.getMaxHealth());
            player.setNoGravity(false);
            player.setShiftKeyDown(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            recoveredPlayers++;
        }

        for (Entity entity : snapshotEntities(server)) {
            if (entity instanceof EntityHappyTeddy teddy && !entity.isRemoved()) {
                teddy.cancelExecution();
            }
        }
        return recoveredPlayers;
    }

    public static int returnAllAbnormalities(MinecraftServer server) {
        int returnedAbnormalities = 0;
        for (Entity entity : snapshotEntities(server)) {
            if (entity.isRemoved()) continue;
            if (entity instanceof EntityEndBird endBird) {
                endBird.discard();
                returnedAbnormalities++;
                continue;
            }
            if (entity instanceof AbstractAbnormality abnormality
                    && abnormality.isAlive() && abnormality.hasEscape()) {
                abnormality.stopEscape();
                returnedAbnormalities++;
            }
        }
        EscapeTracker.getInstance().resetForDay(server);
        return returnedAbnormalities;
    }

    public static int resetAllAiNavigation(MinecraftServer server) {
        ServerLevel facility = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (facility != null) FacilityNavigationCache.resetDynamicLayer(facility);
        return EmployeeRuntimeManager.resetAllForExhibition(server, "展会恢复 AI 与寻路");
    }

    public static EventCleanupReport cleanupEventEntities(MinecraftServer server) {
        int eventEntities = 0;
        int canceledExecutions = 0;
        for (Entity entity : snapshotEntities(server)) {
            if (entity.isRemoved()) continue;
            if (entity instanceof EntityHappyTeddy teddy) {
                teddy.cancelExecution();
                canceledExecutions++;
            }
            if (entity instanceof EntityBlackForestDoor door) {
                door.discard();
                eventEntities++;
            } else if (entity instanceof EntityEndBird endBird) {
                endBird.discard();
                eventEntities++;
            }
        }
        int ordealTargets = KillCommand.queueActiveOrdealTargets(server);
        OrdealData.get(server.overworld()).abortAllActiveOrdeals();
        return new EventCleanupReport(ordealTargets, eventEntities, canceledExecutions);
    }

    public static GaugeAdvanceReport advanceGaugeOne(MinecraftServer server) {
        ServerLevel facility = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) return new GaugeAdvanceReport(false, 0, 0, 0);
        int stages = FacilityManager.debugAdvanceGauge(facility, 1);
        FacilityDayData data = FacilityDayData.get(facility);
        return new GaugeAdvanceReport(true, data.getMeltdownGauge(),
                data.getMeltdownThreshold(), stages);
    }

    public static int maxAllAiEmployees(MinecraftServer server) {
        FacilityPersonnelData personnel = FacilityPersonnelData.get(server);
        int updated = 0;
        for (EmployeeRecord record : personnel.employees()) {
            record.setStats(EmployeeStats.MAX_STAT, EmployeeStats.MAX_STAT,
                    EmployeeStats.MAX_STAT, EmployeeStats.MAX_STAT);
            EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
            if (employee != null) {
                employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                    stats.setFortitude(EmployeeStats.MAX_STAT);
                    stats.setPrudence(EmployeeStats.MAX_STAT);
                    stats.setTemperance(EmployeeStats.MAX_STAT);
                    stats.setJustice(EmployeeStats.MAX_STAT);
                });
                EmployeeStatsApplier.applyAllAttributes(employee);
                EmployeeRuntimeManager.persistNow(record.id());
            }
            updated++;
        }
        if (updated > 0) personnel.changed();
        server.saveEverything(false, true, true);
        return updated;
    }

    private static Map<UUID, ServerPlayer> participants(MinecraftServer server) {
        Map<UUID, ServerPlayer> participants = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            participants.put(player.getUUID(), player);
        }
        for (EmployeeServerPlayer employee : EmployeeRuntimeManager.activePlayers()) {
            participants.put(employee.getUUID(), employee);
        }
        return participants;
    }

    private static List<Entity> snapshotEntities(MinecraftServer server) {
        List<Entity> entities = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            level.getAllEntities().forEach(entities::add);
        }
        return entities;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(2)
                || !isEnabled(player.server)) return;
        ExhibitionCommand.sendPanel(player.createCommandSourceStack());
    }

    public record WorkStopReport(int works, int orders) {
    }

    public record EventCleanupReport(int ordealTargets, int eventEntities, int executions) {
    }

    public record GaugeAdvanceReport(boolean available, int gauge, int threshold,
                                     int stagesAdvanced) {
    }

    public record RecoveryReport(int players, int works, int orders, int abnormalities,
                                 int ordealTargets, int meltdowns, int executions,
                                 int resetEmployees) {
    }
}
