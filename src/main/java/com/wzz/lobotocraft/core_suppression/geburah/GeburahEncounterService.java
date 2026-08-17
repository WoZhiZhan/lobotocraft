package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionEncounter;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GeburahEncounterService implements CoreSuppressionEncounter {
    public static final GeburahEncounterService INSTANCE = new GeburahEncounterService();
    public static final String FORCED_SPECTATOR_TAG = "lobotocraft_geburah_forced_spectator";
    public static final String PARTICIPANT_RECOVERY_TAG = "lobotocraft_geburah_participant_recovery";

    private boolean cleanupInProgress;
    private final Map<UUID, Boolean> employeeInvulnerability = new HashMap<>();

    private GeburahEncounterService() {
    }

    @Override
    public Component getStartDenialReason(ServerPlayer owner) {
        for (ServerPlayer player : owner.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
                return Component.translatable(
                        "message.lobotocraft.core_suppression.geburah.denial.players_outside");
            }
        }
        return null;
    }

    @Override
    public boolean allowsActiveBreaches() {
        return true;
    }

    @Override
    public boolean start(MinecraftServer server, CoreSuppressionData suppression) {
        return start(server, false);
    }

    @Override
    public boolean debugStart(MinecraftServer server, CoreSuppressionData suppression) {
        return start(server, true);
    }

    private boolean start(MinecraftServer server, boolean debug) {
        ServerLevel level = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return false;
        discardStrayGeburah(server);

        EntityGeburah geburah = ModEntities.geburah.get().create(level);
        if (geburah == null) return false;
        boolean disciplinaryOpen = DepartmentProgressData.get(server)
                .isOpen(EmployeeDepartment.DISCIPLINARY);
        var spawn = debug && !disciplinaryOpen
                ? GeburahSpawnLocator.findDebugFallback(level, geburah)
                : GeburahSpawnLocator.find(level, geburah);
        if (spawn.isEmpty() && debug) {
            spawn = GeburahSpawnLocator.findDebugFallback(level, geburah);
        }
        if (spawn.isEmpty()) return false;

        geburah.moveTo(spawn.get().getX() + 0.5D, spawn.get().getY(),
                spawn.get().getZ() + 0.5D, 90.0F, 0.0F);
        geburah.setPersistenceRequired();
        if (!level.addFreshEntity(geburah)) return false;

        Set<UUID> participants = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            participants.add(player.getUUID());
            player.getPersistentData().putBoolean(PARTICIPANT_RECOVERY_TAG, true);
        }
        GeburahEncounterData data = GeburahEncounterData.get(level);
        data.start(geburah.getUUID(), participants);
        GeburahBossBars.start(server, data);
        GeburahBossBars.update(server, geburah);
        reconcileFrozenEmployees(server);
        return true;
    }

    @Override
    public void tick(MinecraftServer server, CoreSuppressionData suppression) {
        if (cleanupInProgress) return;
        ServerLevel level = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) {
            CoreSuppressionManager.failChallenge(server, "设施维度不可用");
            return;
        }
        GeburahEncounterData data = GeburahEncounterData.get(level);
        EntityGeburah geburah = findEntity(level, data);
        if (!data.isActive() || geburah == null || geburah.isRemoved()
                || geburah.level().dimension() != ModDimensions.LOBOTO_KEY) {
            CoreSuppressionManager.failChallenge(server, "Geburah 实体丢失");
            return;
        }
        GeburahBossBars.update(server, geburah);
        reconcileFrozenEmployees(server);
        boolean activeParticipant = server.getPlayerList().getPlayers().stream()
                .anyMatch(player -> data.isParticipant(player.getUUID())
                        && !data.isEliminated(player.getUUID()) && !player.isSpectator()
                        && player.level().dimension() == ModDimensions.LOBOTO_KEY);
        if (!activeParticipant) {
            CoreSuppressionManager.failChallenge(server, "场上已无可继续作战的参与者");
        }
    }

    @Override
    public void finish(MinecraftServer server, boolean success) {
        if (cleanupInProgress) return;
        cleanupInProgress = true;
        try {
            GeburahEncounterData data = GeburahEncounterData.get(server.overworld());
            Set<UUID> affectedPlayers = new HashSet<>(data.participants());
            affectedPlayers.addAll(data.observers());
            discardStrayGeburah(server);
            for (UUID playerUuid : affectedPlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                if (player == null) continue;
                player.setGameMode(GameType.SURVIVAL);
                player.getPersistentData().remove(FORCED_SPECTATOR_TAG);
                player.getPersistentData().remove(PARTICIPANT_RECOVERY_TAG);
                GeburahBleedSync.clear(player);
                teleportToValidSpawn(player);
            }
            for (ServerLevel serverLevel : server.getAllLevels()) {
                for (Entity entity : serverLevel.getAllEntities()) {
                    if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        GeburahBleedSync.clear(living);
                    }
                }
            }
            data.clear();
            restoreFrozenEmployees(server);
            GeburahBossBars.finish(server);
        } finally {
            cleanupInProgress = false;
        }
    }

    @Override
    public boolean ownsParticipantLifecycle() {
        return true;
    }

    @Override
    public boolean freezesAiEmployees() {
        return true;
    }

    @Override
    public int visualStage(MinecraftServer server) {
        EntityGeburah geburah = getActiveEntity(server);
        if (geburah == null) return 0;
        return switch (geburah.combatState().phase()) {
            case NORMAL -> 0;
            case EGO -> 1;
            case EXHAUSTED -> 2;
            case DEFEATED -> 3;
        };
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        ServerLevel level = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return;
        GeburahEncounterData data = GeburahEncounterData.get(level);
        if (!data.isActive()) return;
        if (data.isParticipant(player.getUUID())) {
            player.getPersistentData().putBoolean(PARTICIPANT_RECOVERY_TAG, true);
            player.setGameMode(data.isEliminated(player.getUUID())
                    ? GameType.SPECTATOR : GameType.SURVIVAL);
            if (data.isEliminated(player.getUUID())) {
                player.getPersistentData().putBoolean(FORCED_SPECTATOR_TAG, true);
            }
            if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
                EntityGeburah geburah = findEntity(level, data);
                Vec3 destination = geburah == null
                        ? Vec3.atCenterOf(level.getSharedSpawnPos())
                        : geburah.position().add(0.0D, 0.5D, 4.0D);
                player.teleportTo(level, destination.x, destination.y, destination.z,
                        player.getYRot(), player.getXRot());
            }
        } else {
            data.addObserver(player.getUUID());
            player.setGameMode(GameType.SPECTATOR);
            player.getPersistentData().putBoolean(FORCED_SPECTATOR_TAG, true);
        }
        GeburahBossBars.addPlayer(player, data);
        GeburahBleedSync.sync(player);
    }

    public GeburahEncounterData.DeathOutcome consumeParticipantDeath(ServerPlayer player) {
        ServerLevel level = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return GeburahEncounterData.DeathOutcome.IGNORE;
        return GeburahEncounterData.get(level).consumeDeath(player.getUUID());
    }

    public void enforceDimensionLock(ServerPlayer player, ResourceKey<Level> previousDimension) {
        ServerLevel facility = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) return;
        GeburahEncounterData data = GeburahEncounterData.get(facility);
        if (!data.isActive()) return;
        if (data.isParticipant(player.getUUID()) && player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            EntityGeburah geburah = findEntity(facility, data);
            Vec3 destination = geburah == null ? Vec3.atCenterOf(facility.getSharedSpawnPos())
                    : geburah.position().add(0.0D, 0.5D, 2.0D);
            player.teleportTo(facility, destination.x, destination.y, destination.z,
                    player.getYRot(), player.getXRot());
        } else if (!data.isParticipant(player.getUUID())
                && player.level().dimension() == ModDimensions.LOBOTO_KEY) {
            ServerLevel previous = player.getServer().getLevel(previousDimension);
            if (previous == null || previous.dimension() == ModDimensions.LOBOTO_KEY) {
                previous = player.getServer().overworld();
            }
            Vec3 destination = Vec3.atCenterOf(previous.getSharedSpawnPos());
            player.teleportTo(previous, destination.x, destination.y, destination.z,
                    player.getYRot(), player.getXRot());
        }
    }

    public void cleanupLoginResidue(ServerPlayer player) {
        boolean forcedSpectator = player.getPersistentData().getBoolean(FORCED_SPECTATOR_TAG);
        boolean participantRecovery = player.getPersistentData().getBoolean(PARTICIPANT_RECOVERY_TAG);
        if (!forcedSpectator && !participantRecovery) return;
        ServerLevel facility = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility != null && GeburahEncounterData.get(facility).isActive()) return;
        player.getPersistentData().remove(FORCED_SPECTATOR_TAG);
        player.getPersistentData().remove(PARTICIPANT_RECOVERY_TAG);
        player.setGameMode(GameType.SURVIVAL);
        GeburahBleedSync.clear(player);
        teleportToValidSpawn(player);
    }

    public boolean isParticipant(ServerPlayer player) {
        ServerLevel level = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        return level != null && GeburahEncounterData.get(level).isParticipant(player.getUUID());
    }

    public EntityGeburah getActiveEntity(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.LOBOTO_KEY);
        return level == null ? null : findEntity(level, GeburahEncounterData.get(level));
    }

    public void reconcileServerState(MinecraftServer server) {
        CoreSuppressionData suppression = CoreSuppressionData.get(server.overworld());
        GeburahEncounterData data = GeburahEncounterData.get(server.overworld());
        if (suppression.getActiveType() != CoreSuppressionType.GEBURAH && data.isActive()) {
            finish(server, false);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityGeburah geburah) {
                    reconcileLoadedEntity(geburah, suppression, data);
                }
            }
        }
    }

    public boolean reconcileLoadedEntity(EntityGeburah geburah) {
        if (!(geburah.level() instanceof ServerLevel level)) return false;
        return reconcileLoadedEntity(geburah, CoreSuppressionData.get(level),
                GeburahEncounterData.get(level));
    }

    private boolean reconcileLoadedEntity(EntityGeburah geburah, CoreSuppressionData suppression,
                                          GeburahEncounterData data) {
        boolean discard = GeburahEntityLifecyclePolicy.shouldDiscard(
                suppression.getActiveType(), data.isActive(), data.getEntityUuid(), geburah.getUUID(),
                geburah.level().dimension() == ModDimensions.LOBOTO_KEY);
        if (!discard) return false;
        CombatControlLockService.clearLocksFrom(geburah);
        geburah.discard();
        return true;
    }

    public void onLegitimateDefeat(EntityGeburah geburah) {
        if (cleanupInProgress || !(geburah.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        CoreSuppressionData suppression = CoreSuppressionData.get(level);
        GeburahEncounterData data = GeburahEncounterData.get(level);
        if (suppression.getActiveType() != CoreSuppressionType.GEBURAH
                || !data.isActive() || !geburah.getUUID().equals(data.getEntityUuid())) return;
        CoreSuppressionManager.completeChallenge(server, CoreSuppressionType.GEBURAH, "Geburah 已被击败");
    }

    public void updateBossBars(EntityGeburah geburah) {
        if (geburah.level() instanceof ServerLevel level) {
            GeburahBossBars.update(level.getServer(), geburah);
        }
    }

    private EntityGeburah findEntity(ServerLevel level, GeburahEncounterData data) {
        UUID uuid = data.getEntityUuid();
        if (uuid == null) return null;
        Entity entity = level.getEntity(uuid);
        return entity instanceof EntityGeburah geburah ? geburah : null;
    }

    private void discardStrayGeburah(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityGeburah geburah)) continue;
                CombatControlLockService.clearLocksFrom(geburah);
                geburah.discard();
            }
        }
    }

    private void reconcileFrozenEmployees(MinecraftServer server) {
        for (EmployeeServerPlayer employee : EmployeeRuntimeManager.activePlayers()) {
            if (employee.server != server || employee.isRemoved()) continue;
            employeeInvulnerability.putIfAbsent(employee.getUUID(), employee.isInvulnerable());
            employee.setInvulnerable(true);
            employee.clearNavigationInput();
        }
    }

    private void restoreFrozenEmployees(MinecraftServer server) {
        for (EmployeeServerPlayer employee : EmployeeRuntimeManager.activePlayers()) {
            if (employee.server != server) continue;
            Boolean original = employeeInvulnerability.remove(employee.getUUID());
            if (original != null) employee.setInvulnerable(original);
        }
        employeeInvulnerability.clear();
    }

    private void teleportToValidSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        if (respawnLevel == null) respawnLevel = server.overworld();
        var respawnPos = player.getRespawnPosition();
        Vec3 destination = null;
        if (respawnPos != null) {
            destination = Player.findRespawnPositionAndUseSpawnBlock(respawnLevel, respawnPos,
                    player.getRespawnAngle(), player.isRespawnForced(), true).orElse(null);
        }
        if (destination == null) destination = Vec3.atCenterOf(respawnLevel.getSharedSpawnPos());
        player.teleportTo(respawnLevel, destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
    }
}
