package com.lobotocraft.facility;

import com.lobotocraft.facility.api.DayCompletionRequirements;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownPolicy;
import com.lobotocraft.facility.data.OrdealContentPolicy;
import com.lobotocraft.facility.data.OrdealSchedulePlanner;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.day.DayTransitionService;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.lobotocraft.facility.network.FacilityStatePacket;
import com.lobotocraft.facility.mixin.access.CompanyDailyDataAccessor;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.command.KillCommand;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBird;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggEye;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggHigh;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggSmall;
import com.wzz.lobotocraft.entity.abnormality.EntityIronMaiden;
import com.wzz.lobotocraft.entity.abnormality.EntityIsharmla;
import com.wzz.lobotocraft.entity.abnormality.IsharmlaSpawnData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.event.definition.work.WorkPreExtractionEvent;
import com.wzz.lobotocraft.event.listener.AmberDawnEvent;
import com.wzz.lobotocraft.event.listener.BlueMiddayEvent;
import com.wzz.lobotocraft.event.listener.CrimsonDawnEvent;
import com.wzz.lobotocraft.event.listener.CrimsonNoonEvent;
import com.wzz.lobotocraft.event.listener.GreenDawnEvent;
import com.wzz.lobotocraft.event.listener.GreenNoonEvent;
import com.wzz.lobotocraft.event.listener.VioletDawnEvent;
import com.wzz.lobotocraft.event.listener.VioletNoonEvent;
import com.wzz.lobotocraft.employee.DepartmentProgressService;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.exhibition.ExhibitionSafetyService;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.world.data.OrdealData;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.RoomEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

public final class FacilityManager {
    private static final int MELTDOWN_DURATION_TICKS = 20 * 60;
    private static final Set<WorkManager.WorkSession> MELTDOWN_WORKS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private FacilityManager() {
    }

    public static void onWorkStarted(ServerPlayer player, AbstractAbnormality abnormality) {
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) return;
        ServerLevel level = player.serverLevel();
        FacilityDayData data = syncDay(level, player);
        WorkManager.WorkSession session = WorkManager.getWorkSession(player);

        if (data.getActiveMeltdowns().containsKey(abnormality.getUUID())) {
            if (session != null) MELTDOWN_WORKS.add(session);
        }
        if (data.advanceGauge()) {
            activateStage(level, data);
        }
        syncAll(level.getServer(), data);
    }

    public static void modifySuccessRate(WorkPreExtractionEvent event) {
        IAbnormality abnormality = event.getAbnormality();
        if (!(abnormality instanceof AbstractAbnormality entity)) return;
        if (!(event.getPlayer().level() instanceof ServerLevel level)) return;
        if (MELTDOWN_WORKS.contains(event.getSession())) return;

        event.setSuccessRate(event.getSuccessRate()
                - repeatedWorkPenalty(level, entity));
    }

    /** Original Lobotomy Corporation repeated-work penalty for one containment unit. */
    public static float repeatedWorkPenalty(ServerLevel level, AbstractAbnormality abnormality) {
        if (level == null || abnormality == null || level.dimension() != ModDimensions.LOBOTO_KEY
                || isMeltdownTarget(level, abnormality)) return 0.0F;
        RiskLevel risk = abnormality.getRiskLevel();
        int stepPercent;
        int maximum;
        switch (risk) {
            case WAW -> { stepPercent = 4; maximum = 8; }
            case ALEPH -> { stepPercent = 6; maximum = 5; }
            default -> { return 0.0F; }
        }
        int count = Math.min(FacilityDayData.get(level)
                .getConsecutiveWorks(abnormality.getUUID()), maximum);
        return count * stepPercent / 100.0F;
    }

    public static int repeatedWorkPenaltyPercent(ServerLevel level, AbstractAbnormality abnormality) {
        return Math.round(repeatedWorkPenalty(level, abnormality) * 100.0F);
    }

    private static boolean isMeltdownTarget(ServerLevel level, AbstractAbnormality abnormality) {
        return FacilityDayData.get(level).getActiveMeltdowns().containsKey(abnormality.getUUID());
    }

    public static void onWorkCompleted(WorkCompleteEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (level.dimension() != ModDimensions.LOBOTO_KEY) return;
        FacilityDayData data = syncDay(level, event.getEntity());
        boolean meltdownWork = MELTDOWN_WORKS.remove(event.getSession());
        boolean activeMeltdown = event.getAbnormality() instanceof AbstractAbnormality entity
                && data.getActiveMeltdowns().containsKey(entity.getUUID());
        boolean completedWork = MeltdownPolicy.countsForFacilityProgress(event.isForcedEnd());

        if (event.getPeOutput() > 0) data.addEnergy(event.getPeOutput());
        if (completedWork && (meltdownWork || activeMeltdown)
                && event.getAbnormality() instanceof AbstractAbnormality entity
                && data.resolveMeltdown(entity.getUUID())) {
            event.getEntity().displayClientMessage(Component.literal(
                    "逆卡巴拉熔毁已解除：" + entity.getAbnormalityName())
                .withStyle(ChatFormatting.GREEN), false);
            data.notifyState(5);
        }
        if (completedWork && !meltdownWork && !activeMeltdown
                && event.getAbnormality() instanceof AbstractAbnormality entity) {
            RiskLevel risk = event.getAbnormality().getRiskLevel();
            if (risk == RiskLevel.WAW || risk == RiskLevel.ALEPH) {
                int maximum = risk == RiskLevel.WAW ? 8 : 5;
                data.incrementConsecutiveWorks(entity.getUUID(), maximum);
            }
        }
        syncAll(level.getServer(), data);
    }

    public static boolean canCompleteDay(ServerPlayer player) {
        return DayCompletionRequirements.allSatisfied(player, syncDay(player.serverLevel(), player));
    }

    /**
     * Credits continuous tool output to the same facility-wide pool used by
     * ordinary PE work. The machine, not its operator or sacrifice, owns the
     * production, matching the original game's global EnergyModel behavior.
     */
    public static double creditToolEnergy(ServerLevel level, double amount) {
        if (level == null || level.dimension() != ModDimensions.LOBOTO_KEY
                || !Double.isFinite(amount) || amount <= 0.0D) {
            return 0.0D;
        }
        FacilityDayData data = FacilityDayData.get(level);
        int visibleEnergyBefore = data.getEnergy();
        double credited = creditEnergy(data, amount);
        // Facility packets currently expose whole energy. Avoid broadcasting up
        // to ten unchanged packets per second once the original timer reaches 0.1s.
        if (data.getEnergy() != visibleEnergyBefore) syncAll(level.getServer(), data);
        return credited;
    }

    static double creditEnergy(FacilityDayData data, double amount) {
        if (data == null || !Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double before = data.getExactEnergy();
        data.addEnergy(amount);
        return data.getExactEnergy() - before;
    }

    public static FacilityDayData syncDay(ServerLevel level, ServerPlayer player) {
        FacilityDayData data = FacilityDayData.get(level);
        int[] playerDay = {1};
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
            .ifPresent(daily -> playerDay[0] = ((CompanyDailyDataAccessor) daily).facility$getStoredDay());
        // FacilityDayData is authoritative. A stale or edited player capability
        // must never fast-forward global schedules, meltdowns, or ordeals.
        if (playerDay[0] != data.getDay()) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(daily -> {
                daily.setOwner(player);
                daily.setCurrentDay(data.getDay());
                daily.setTodayWorkCount(0);
                if (daily.isArmorLocked()) daily.unlockArmor();
                daily.setHasSleep(false);
            });
        }
        if (!data.isSchedulePrepared()) {
            data.prepareSchedule(level.getSeed());
            prepareAndSelectNextOrdeal(data, level.getSeed());
        }
        return data;
    }

    public static void beginDay(ServerLevel level, int day) {
        FacilityDayData data = FacilityDayData.get(level);
        if (day <= data.getDay()) {
            syncAll(level.getServer(), data);
            return;
        }
        data.beginDay(day);
        DepartmentProgressService.onDayStarted(level.getServer(), day);
        recoverAbnormalitiesForNewDay(level.getServer());
        clearStatusEffectsForNewDay(level.getServer());
        EscapeTracker.getInstance().resetForDay(level.getServer());
        data.prepareSchedule(level.getSeed());
        prepareAndSelectNextOrdeal(data, level.getSeed());
        syncAll(level.getServer(), data);
    }

    /** Forces the current meltdown stage through the normal activation path for diagnostics. */
    public static boolean debugTriggerCurrentStage(ServerLevel level) {
        FacilityDayData data = FacilityDayData.get(level);
        int beforeLevel = data.getMeltdownLevel();
        data.debugSetMeltdownGauge(data.getMeltdownThreshold());
        activateStage(level, data);
        syncAll(level.getServer(), data);
        // The normal activation path always consumes a stage below level X.
        return beforeLevel != data.getMeltdownLevel();
    }

    /** Advances Qliphoth gauge points through the same stage activation path as work. */
    public static int debugAdvanceGauge(ServerLevel level, int amount) {
        if (amount <= 0) return 0;
        FacilityDayData data = FacilityDayData.get(level);
        int stagesAdvanced = 0;
        for (int i = 0; i < amount; i++) {
            int beforeLevel = data.getMeltdownLevel();
            if (data.advanceGauge()) activateStage(level, data);
            if (data.getMeltdownLevel() != beforeLevel) stagesAdvanced++;
        }
        syncAll(level.getServer(), data);
        return stagesAdvanced;
    }

    /** Repeats the normal stage trigger until it can no longer advance. */
    public static int debugAdvanceStages(ServerLevel level, int amount) {
        if (amount <= 0) return 0;
        int stagesAdvanced = 0;
        for (int i = 0; i < amount && debugTriggerCurrentStage(level); i++) stagesAdvanced++;
        return stagesAdvanced;
    }

    /** Resets the current facility day to the opening state of a forced suppression. */
    public static void debugResetSuppressionProgress(ServerLevel level) {
        FacilityDayData data = FacilityDayData.get(level);
        data.beginDay(data.getDay());
        data.prepareSchedule(level.getSeed());
        prepareAndSelectNextOrdeal(data, level.getSeed());
        syncAll(level.getServer(), data);
    }

    /**
     * Debug-only direct trigger that still goes through the production ordeal
     * generators and their spawn validation.
     */
    public static boolean debugTriggerOrdeal(ServerLevel level,
                                              OrdealTier tier, int color) {
        if (tier == null || tier == OrdealTier.NONE) return false;
        FacilityDayData dayData = FacilityDayData.get(level);
        if (!OrdealSchedulePlanner.isUnlocked(dayData.getDay(), tier)) return false;
        if (hasActiveOrdealAtTier(OrdealData.get(level), tier)) return false;

        boolean triggered = switch (tier) {
            case DAWN -> triggerDawn(level, color);
            case NOON -> triggerNoon(level, color);
            case DUSK, MIDNIGHT -> showUnfinishedOrdeal(level, tier);
            default -> false;
        };
        if (triggered) {
            if (tier == OrdealTier.DAWN) dayData.markDawnTriggered();
            if (tier == OrdealTier.NOON) dayData.markNoonTriggered();
            syncAll(level.getServer(), dayData);
        }
        return triggered;
    }

    private static void recoverAbnormalitiesForNewDay(MinecraftServer server) {
        // Day completion may occur while an ordeal is still alive. Queue all
        // ordeal bodies before resetting their persisted authority so no
        // previous-day combatant survives as an untracked entity.
        KillCommand.queueActiveOrdealTargets(server);
        OrdealData.get(server.overworld()).syncDay(FacilityDayData.get(server.overworld()).getDay());
        if (EntityIsharmla.restoreSkadiForNewDay(server)) {
            IsharmlaSpawnData.get(server).cancelPendingTransitions();
        }
        // Take a snapshot first: containment return may transfer an entity to
        // another dimension and remove the source instance from the live list.
        // Iterating the live entity collection while doing that can skip or
        // double-process escaped abnormalities.
        List<AbstractAbnormality> escaped = new ArrayList<>();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof AbstractAbnormality abnormality && abnormality.hasEscape()) {
                    escaped.add(abnormality);
                }
            }
        }
        for (AbstractAbnormality abnormality : escaped) {
            if (!abnormality.isRemoved() && abnormality.isAlive() && abnormality.hasEscape()) {
                abnormality.stopEscape();
            }
        }
        // Clear the obsolete per-player sleep latch only after every loaded abnormality has seen the global transition.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                data.setHasSleep(false);
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(player,
                        new com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket(
                                data.getCurrentDay(), data.getTodayWorkCount(), data.isArmorLocked(), false));
            });
        }
    }

    static void clearStatusEffectsForNewDay(MinecraftServer server) {
        clearStatusEffects(server.getPlayerList().getPlayers());
        clearStatusEffects(EmployeeRuntimeManager.activePlayers());
    }

    static void clearStatusEffects(Iterable<? extends ServerPlayer> players) {
        for (ServerPlayer player : players) player.removeAllEffects();
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        FacilityDayData data = FacilityDayData.get(overworld);
        long gameTime = overworld.getGameTime();
        boolean protectedMeltdownsRemoved = removeProtectedMeltdowns(server, data);
        if (gameTime % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof AbstractAbnormality abnormality) {
                        CoreSuppressionManager.retireLegacyMeltdown(abnormality);
                    }
                }
            }
        }
        // ActiveMeltdowns is only a core-suppression view. Drop stale entries
        // from older builds as soon as a normal company day is running.
        if (!CoreSuppressionManager.isSuppressionActive(overworld)
                && !data.getActiveMeltdowns().isEmpty()) {
            data.getActiveMeltdowns().clear();
            data.notifyState(0);
            syncAll(server, data);
        }
        if (!CoreSuppressionManager.isSuppressionActive(overworld)) {
            DayTransitionService.tick(server, data, gameTime);
            if (gameTime % 20 == 0) syncAll(server, data);
            return;
        }
        List<UUID> expired = data.getActiveMeltdowns().entrySet().stream()
            .filter(entry -> entry.getValue().deadline() <= gameTime)
            .map(java.util.Map.Entry::getKey)
            .toList();
        for (UUID target : expired) {
            FacilityDayData.ActiveMeltdown meltdown = data.getActiveMeltdowns().get(target);
            int failedLevel = meltdown.level();
            String targetCode = meltdown.targetCode();
            AbstractAbnormality loaded = findAbnormality(server, target);
            data.resolveMeltdown(target);
            // Entries written before target identity was persisted are retired
            // without a penalty when the entity is no longer loaded.
            if (!MeltdownPolicy.isProtectedContainmentCode(targetCode)
                    && (loaded != null || targetCode != null && !targetCode.isBlank())) {
                failMeltdown(server, data, target, failedLevel);
            }
        }
        DayTransitionService.tick(server, data, gameTime);
        if (protectedMeltdownsRemoved || !expired.isEmpty() || gameTime % 20 == 0) syncAll(server, data);
    }

    public static void sync(ServerPlayer player) {
        FacilityDayData data = syncDay(player.serverLevel(), player);
        FacilityNetwork.send(player, FacilityStatePacket.from(
                data, player.server.overworld().getGameTime(), player));
        com.wzz.lobotocraft.employee.DepartmentProgressData progress =
                com.wzz.lobotocraft.employee.DepartmentProgressData.get(player.server);
        progress.prepareForDay(data.getDay());
        if (progress.pendingDay() > 0) {
            DepartmentProgressService.open(player, true);
        }
    }

    private static void activateStage(ServerLevel level, FacilityDayData data) {
        OrdealData.get(level).syncDay(data.getDay());
        int levelNumber = data.getMeltdownLevel();
        OrdealTier scheduledTier = data.getNextOrdeal();
        boolean ordealScheduled = scheduledTier != OrdealTier.NONE;
        boolean unfinishedOrdeal = OrdealContentPolicy.isUnfinished(scheduledTier);
        boolean exhibitionMode = ExhibitionSafetyService.isEnabled(level.getServer());
        boolean ordealTriggered = ordealScheduled && !exhibitionMode && triggerOrdeal(level, data);
        boolean suppressionActive = CoreSuppressionManager.isSuppressionActive(level);
        boolean canActivateMeltdown = !exhibitionMode
                && MeltdownPolicy.canActivate(suppressionActive, ordealTriggered);
        boolean meltdownsActivated = false;
        if (canActivateMeltdown) {
            meltdownsActivated = activateMeltdowns(level, data, levelNumber);
        }

        if (!unfinishedOrdeal && !exhibitionMode) {
            data.notifyState(MeltdownPolicy.stageNotificationType(suppressionActive, ordealTriggered));
        }
        if (meltdownsActivated) {
            CrimsonDawnEvent.sendOrdealTitle(level.getServer(), "逆卡巴拉警报",
                "第 " + roman(levelNumber) + " 级熔毁",
                "请在六十秒内完成受影响收容单元的工作。", 0xD84A4A);
        }
        CoreSuppressionManager.onQliphothMeltdown(level);
        data.finishMeltdownStage();
        prepareAndSelectNextOrdeal(data, level.getSeed());
    }

    private static boolean activateMeltdowns(ServerLevel level, FacilityDayData data, int levelNumber) {
        List<AbstractAbnormality> candidates = new ArrayList<>();
        int containmentCount = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractAbnormality abnormality
                && abnormality.isAlive()
                && !abnormality.hasEscape()
                && !abnormality.isToolType()
                && !isEndBirdEncounterEntity(abnormality)
                && !isMeltdownProtected(level, abnormality)
                && !isCurrentSuppressionDepartment(level, abnormality)
                && abnormality.getQliphothCounter() > 0) {
                containmentCount++;
                if (WorkManager.isAbnormalityBeingWorked(abnormality)
                        || data.getActiveMeltdowns().containsKey(abnormality.getUUID())) continue;
                candidates.add(abnormality);
            }
        }
        if (candidates.isEmpty()) return false;
        RandomSource random = RandomSource.create(level.getSeed() ^ ((long) data.getDay() << 32) ^ levelNumber);
        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        int requested = (containmentCount * levelNumber + 9) / 10;
        int count = Math.min(candidates.size(), requested);
        long deadline = level.getGameTime() + MELTDOWN_DURATION_TICKS;
        for (int i = 0; i < count; i++) {
            AbstractAbnormality target = candidates.get(i);
            data.addMeltdown(target.getUUID(), deadline, levelNumber, target.getAbnormalityCode());
        }
        return count > 0;
    }

    /** Direct Apocalypse Bird egg overloads do not advance the facility gauge. */
    public static int activateEndBirdMeltdowns(ServerLevel level, int maximumTargets) {
        if (level == null || maximumTargets <= 0 || level.dimension() != ModDimensions.LOBOTO_KEY) return 0;
        if (ExhibitionSafetyService.isEnabled(level.getServer())) return 0;
        FacilityDayData data = FacilityDayData.get(level);
        List<AbstractAbnormality> candidates = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractAbnormality abnormality
                    && abnormality.isAlive()
                    && !abnormality.hasEscape()
                    && !abnormality.isToolType()
                    && !isEndBirdEncounterEntity(abnormality)
                    && abnormality.getQliphothCounter() > 0
                    && !data.getActiveMeltdowns().containsKey(abnormality.getUUID())
                    && !isMeltdownProtected(level, abnormality)
                    && !isCurrentSuppressionDepartment(level, abnormality)) {
                candidates.add(abnormality);
            }
        }
        if (candidates.isEmpty()) return 0;
        Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        int count = Math.min(maximumTargets, candidates.size());
        int levelNumber = Math.max(1, data.getMeltdownLevel());
        long deadline = level.getGameTime() + MELTDOWN_DURATION_TICKS;
        for (int i = 0; i < count; i++) {
            AbstractAbnormality target = candidates.get(i);
            data.addMeltdown(target.getUUID(), deadline, levelNumber, target.getAbnormalityCode());
        }
        CrimsonDawnEvent.sendOrdealTitle(level.getServer(), "终末鸟警报",
                "逆卡巴拉熔毁", "鸟蛋使 " + count + " 个收容单元进入熔毁。", 0xD84A4A);
        syncAll(level.getServer(), data);
        return count;
    }

    private static boolean isEndBirdEncounterEntity(AbstractAbnormality abnormality) {
        return abnormality instanceof EntityEndBird
                || abnormality instanceof EntityEndBirdEggSmall
                || abnormality instanceof EntityEndBirdEggHigh
                || abnormality instanceof EntityEndBirdEggEye;
    }

    private static void failMeltdown(MinecraftServer server, FacilityDayData data, UUID target, int failedLevel) {
        AbstractAbnormality abnormality = findAbnormality(server, target);
        if (abnormality != null && abnormality.level() instanceof ServerLevel level
                && isMeltdownProtected(level, abnormality)) {
            return;
        }
        int penalty = 5 * Math.max(1, failedLevel);
        data.subtractEnergy(penalty);
        data.notifyState(3);
        if (abnormality != null) {
            int counter = abnormality.getQliphothCounter();
            if (counter > 0) abnormality.decreaseQliphothCounter(counter);
            server.getPlayerList().broadcastSystemMessage(
                Component.literal("熔毁失败：" + abnormality.getAbnormalityName() + "，能源 -" + penalty)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    private static AbstractAbnormality findAbnormality(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractAbnormality abnormality) return abnormality;
        }
        return null;
    }

    private static boolean removeProtectedMeltdowns(MinecraftServer server, FacilityDayData data) {
        List<UUID> protectedTargets = data.getActiveMeltdowns().entrySet().stream()
            .filter(entry -> {
                if (MeltdownPolicy.isProtectedContainmentCode(entry.getValue().targetCode())) return true;
                AbstractAbnormality abnormality = findAbnormality(server, entry.getKey());
                return abnormality != null
                    && abnormality.level() instanceof ServerLevel level
                    && isMeltdownProtected(level, abnormality);
            })
            .map(java.util.Map.Entry::getKey)
            .toList();
        for (UUID target : protectedTargets) data.resolveMeltdown(target);
        return !protectedTargets.isEmpty();
    }

    private static boolean isMeltdownProtected(ServerLevel level, AbstractAbnormality abnormality) {
        boolean protectedType = abnormality instanceof EntityDarkSkadi
            || abnormality instanceof EntityIronMaiden;
        String boundRoomCode = "";
        if (abnormality.getHomeRoomDimension() != null && abnormality.getHomeRoomMarker() != null) {
            RoomEntry room = CompanyRoomData.get(level).findRoom(
                abnormality.getHomeRoomDimension(), abnormality.getHomeRoomMarker());
            if (room != null) boundRoomCode = room.abnormalityCode;
        }
        return !MeltdownPolicy.canReceiveDirectMeltdown(
            abnormality.getAbnormalityCode(), boundRoomCode, protectedType);
    }

    private static boolean isCurrentSuppressionDepartment(ServerLevel level,
                                                            AbstractAbnormality abnormality) {
        CoreSuppressionData suppression = CoreSuppressionData.get(level);
        CoreSuppressionType activeType = suppression.isActive() ? suppression.getActiveType() : null;
        if (activeType == null || abnormality.getHomeRoomDimension() == null
                || abnormality.getHomeRoomMarker() == null) return false;
        RoomEntry room = CompanyRoomData.get(level).findRoom(
                abnormality.getHomeRoomDimension(), abnormality.getHomeRoomMarker());
        return room != null && activeType.getId().equalsIgnoreCase(room.departmentId);
    }

    /**
     * Selects the entry chosen at the day boundary. Calling this after every
     * stage is intentional: it only reads the persisted table and therefore
     * cannot reroll a color after a restart.
     */
    private static void prepareAndSelectNextOrdeal(FacilityDayData data, long worldSeed) {
        data.prepareSchedule(worldSeed);
        int stage = data.getMeltdownLevel();
        data.setNextOrdeal(data.getScheduledOrdeal(stage), data.getScheduledOrdealColor(stage));
    }

    private static boolean triggerOrdeal(ServerLevel level, FacilityDayData data) {
        boolean triggered = switch (data.getNextOrdeal()) {
            case DAWN -> triggerDawn(level, data.getNextOrdealColor());
            case NOON -> triggerNoon(level, data.getNextOrdealColor());
            case DUSK, MIDNIGHT -> showUnfinishedOrdeal(level, data.getNextOrdeal());
            default -> false;
        };
        if (triggered && data.getNextOrdeal() == OrdealTier.DAWN) data.markDawnTriggered();
        if (triggered && data.getNextOrdeal() == OrdealTier.NOON) data.markNoonTriggered();
        return triggered;
    }

    private static boolean showUnfinishedOrdeal(ServerLevel level, OrdealTier tier) {
        String name = tier == OrdealTier.DUSK ? "黄昏考验" : "午夜考验";
        CrimsonDawnEvent.sendOrdealTitle(level.getServer(), name, "未完工",
                "当前版本不会生成任何实体。", 0x888888);
        Component message = Component.literal("[" + name + "] 未完工，当前版本不会生成任何实体。")
                .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
        return true;
    }

    private static boolean triggerDawn(ServerLevel level, int color) {
        return switch (color) {
            case 1 -> CrimsonDawnEvent.triggerBloodDawn(level);
            case 2 -> GreenDawnEvent.triggerGreenDawn(level);
            case 3 -> VioletDawnEvent.triggerVioletDawn(level);
            default -> AmberDawnEvent.triggerAmberDawn(level);
        };
    }

    private static boolean triggerNoon(ServerLevel level, int color) {
        return switch (color) {
            case 1 -> BlueMiddayEvent.triggerBlueMidday(level);
            case 2 -> VioletNoonEvent.triggerVioletNoon(level);
            case 3 -> GreenNoonEvent.triggerGreenNoon(level);
            default -> CrimsonNoonEvent.triggerCrimsonNoon(level);
        };
    }

    private static boolean hasActiveOrdealAtTier(OrdealData data, OrdealTier tier) {
        return switch (tier) {
            case DAWN -> data.hasActiveDawn();
            case NOON -> data.hasActiveMidday();
            case DUSK, MIDNIGHT -> data.hasActiveLateOrdeal();
            default -> false;
        };
    }

    public static void syncAll(MinecraftServer server, FacilityDayData data) {
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isRemoved() || player.isChangingDimension()) continue;
            FacilityNetwork.send(player, FacilityStatePacket.from(data, gameTime, player));
        }
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }
}
