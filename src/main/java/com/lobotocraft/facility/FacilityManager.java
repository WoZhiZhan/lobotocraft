package com.lobotocraft.facility;

import com.lobotocraft.facility.api.DayCompletionRequirements;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownCause;
import com.lobotocraft.facility.data.MeltdownEndReason;
import com.lobotocraft.facility.data.MeltdownFailurePolicy;
import com.lobotocraft.facility.data.MeltdownLifecycle;
import com.lobotocraft.facility.data.MeltdownDepartmentPolicy;
import com.lobotocraft.facility.data.MeltdownPolicy;
import com.lobotocraft.facility.data.MeltdownStageContext;
import com.lobotocraft.facility.data.MeltdownTargetLocator;
import com.lobotocraft.facility.data.OrdealContentPolicy;
import com.lobotocraft.facility.data.OrdealSchedulePlanner;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.day.DayTransitionService;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.lobotocraft.facility.network.FacilityStatePacket;
import com.lobotocraft.facility.mixin.access.CompanyDailyDataAccessor;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
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
import com.wzz.lobotocraft.event.definition.company.FacilityQliphothStageEvent;
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
import com.wzz.lobotocraft.init.ModSounds;
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
import net.minecraftforge.common.MinecraftForge;

public final class FacilityManager {
    private static final Set<WorkManager.WorkSession> MELTDOWN_WORKS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private FacilityManager() {
    }

    public static void onWorkStarted(ServerPlayer player, AbstractAbnormality abnormality) {
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) return;
        ServerLevel level = player.serverLevel();
        FacilityDayData data = syncDay(level, player);
        WorkManager.WorkSession session = WorkManager.getWorkSession(player);

        boolean activeMeltdown = data.getActiveMeltdowns().containsKey(abnormality.getUUID());
        if (activeMeltdown) {
            if (session != null) MELTDOWN_WORKS.add(session);
            if (MeltdownLifecycle.resolve(data, abnormality.getUUID())) {
                player.displayClientMessage(Component.translatable(
                        "message.lobotocraft.meltdown.resolved",
                        abnormality.getLocalizedAbnormalityName())
                    .withStyle(ChatFormatting.GREEN), false);
                data.notifyState(5);
            }
        }
        if (MeltdownPolicy.advancesGaugeForWork(activeMeltdown) && data.advanceGauge()) {
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
        int count = FacilityDayData.get(level).getConsecutiveWorks(abnormality.getUUID());
        return repeatedWorkPenaltyPercent(abnormality.getRiskLevel(), count) / 100.0F;
    }

    public static int repeatedWorkPenaltyPercent(RiskLevel risk, int count) {
        int stepPercent;
        int maximumPercent;
        switch (risk) {
            case WAW -> { stepPercent = 4; maximumPercent = 24; }
            case ALEPH -> { stepPercent = 6; maximumPercent = 32; }
            default -> { return 0; }
        }
        return Math.min(Math.max(0, count) * stepPercent, maximumPercent);
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

    /** Resolves every currently active meltdown without changing the facility day or gauge. */
    public static int debugClearMeltdowns(ServerLevel level) {
        if (level == null) return 0;
        FacilityDayData data = FacilityDayData.get(level);
        int cleared = MeltdownLifecycle.clear(data, MeltdownEndReason.WORK_COMPLETED);
        if (cleared > 0) data.notifyState(5);
        syncAll(level.getServer(), data);
        return cleared;
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
        recoverEndBirdEncounterForNewDay(server);
        // Take a snapshot first: containment return may transfer an entity to
        // another dimension and remove the source instance from the live list.
        // Iterating the live entity collection while doing that can skip or
        // double-process abnormalities. Contained and non-escapable entities also reset daily.
        List<AbstractAbnormality> abnormalities = new ArrayList<>();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof AbstractAbnormality abnormality) {
                    abnormalities.add(abnormality);
                }
            }
        }
        for (AbstractAbnormality abnormality : abnormalities) {
            abnormality.recoverForNewFacilityDay();
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

    /** End Bird and its eggs are a temporary encounter, so day recovery restores the three birds. */
    private static void recoverEndBirdEncounterForNewDay(MinecraftServer server) {
        List<EntityEndBird> endBirds = new ArrayList<>();
        List<Entity> orphanEggs = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityEndBird endBird) endBirds.add(endBird);
                else if (entity instanceof EntityEndBirdEggSmall
                        || entity instanceof EntityEndBirdEggHigh
                        || entity instanceof EntityEndBirdEggEye) {
                    orphanEggs.add(entity);
                }
            }
        }
        for (EntityEndBird endBird : endBirds) {
            if (!endBird.isRemoved() && endBird.level() instanceof ServerLevel level) {
                endBird.recoverForNewDay(level);
            }
        }
        for (Entity egg : orphanEggs) {
            if (!egg.isRemoved()) EntityEndBird.discardEggForDayRecovery(egg);
        }
    }

    static void clearStatusEffectsForNewDay(MinecraftServer server) {
        clearStatusEffects(server.getPlayerList().getPlayers());
        clearStatusEffects(EmployeeRuntimeManager.activePlayers());
    }

    static void clearStatusEffects(Iterable<? extends ServerPlayer> players) {
        for (ServerPlayer player : players) {
            com.wzz.lobotocraft.util.ControlStateRecoveryService.clearTransientControls(player);
            com.wzz.lobotocraft.event.listener.MentalValueEventHandler.forceRecover(player);
            player.removeAllEffects();
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        FacilityDayData data = FacilityDayData.get(overworld);
        long gameTime = overworld.getGameTime();
        if (FacilityPauseState.isPaused(server)) {
            MeltdownLifecycle.advance(data, gameTime);
            if (gameTime % 20 == 0) syncAll(server, data);
            return;
        }
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
        boolean suppressionActive = CoreSuppressionManager.isSuppressionActive(overworld);
        // Only definitions that require core suppression are cancelled here.
        // Bird-egg and future independent causes keep their own lifecycle.
        int suppressionBoundRemoved = suppressionActive ? 0
                : MeltdownLifecycle.endSuppressionBound(data);
        if (suppressionBoundRemoved > 0) {
            data.notifyState(0);
        }
        reconcileMeltdownTimeRates(server, data, gameTime);
        List<UUID> expired = MeltdownLifecycle.advance(data, gameTime);
        for (UUID target : expired) {
            FacilityDayData.ActiveMeltdown meltdown = data.getActiveMeltdowns().get(target);
            if (meltdown == null) continue;
            String targetCode = meltdown.targetCode();
            AbstractAbnormality loaded = findAbnormality(server, target, meltdown);
            MeltdownLifecycle.end(data, target, MeltdownEndReason.EXPIRED);
            // Entries written before target identity was persisted are retired
            // without a penalty when the entity is no longer loaded.
            if (!MeltdownPolicy.isProtectedContainmentCode(targetCode)
                    && (loaded != null || targetCode != null && !targetCode.isBlank())) {
                failMeltdown(server, data, target, meltdown);
            }
        }
        DayTransitionService.tick(server, data, gameTime);
        if (protectedMeltdownsRemoved || suppressionBoundRemoved > 0
                || !expired.isEmpty() || gameTime % 20 == 0) syncAll(server, data);
    }

    private static void reconcileMeltdownTimeRates(MinecraftServer server,
                                                    FacilityDayData data,
                                                    long gameTime) {
        for (UUID target : List.copyOf(data.getActiveMeltdowns().keySet())) {
            FacilityDayData.ActiveMeltdown meltdown = data.getActiveMeltdowns().get(target);
            if (meltdown == null
                    || MeltdownLifecycle.definition(meltdown.cause()).excludeWorkingTargets()) {
                continue;
            }
            AbstractAbnormality abnormality = findAbnormality(server, target, meltdown);
            if (abnormality == null) continue;
            int desiredRate = WorkManager.isAbnormalityBeingWorked(abnormality)
                    ? 0 : MeltdownLifecycle.NORMAL_TIME_RATE_PERMILLE;
            if (meltdown.timeRatePermille() != desiredRate) {
                MeltdownLifecycle.setTimeRate(data, target, gameTime, desiredRate);
            }
        }
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
                && MeltdownPolicy.normalMeltdownEnabled(data.getDay())
                && MeltdownPolicy.canActivate(suppressionActive, ordealTriggered);
        int previousActiveMeltdowns = data.getActiveMeltdowns().size();
        int meltdownsActivated = 0;
        if (canActivateMeltdown) {
            meltdownsActivated = activateMeltdowns(level, data, levelNumber);
        }

        if (!unfinishedOrdeal && !exhibitionMode) {
            data.notifyState(MeltdownPolicy.stageNotificationType(suppressionActive, ordealTriggered));
        }
        if (MeltdownPolicy.shouldAnnounceActivation(
                previousActiveMeltdowns, meltdownsActivated)) {
            CrimsonDawnEvent.sendOrdealTitle(level.getServer(), "逆卡巴拉警报",
                "第 " + roman(levelNumber) + " 级熔毁",
                 "请在七十五秒内完成受影响收容单元的工作。", 0xD84A4A);
        }
        if (MeltdownPolicy.shouldAnnounceActivation(
                previousActiveMeltdowns, meltdownsActivated)) {
            playMeltdownAlarm(level.getServer());
        }
        CoreSuppressionManager.onQliphothMeltdown(level);
        MinecraftForge.EVENT_BUS.post(new FacilityQliphothStageEvent(level, levelNumber));
        data.finishMeltdownStage();
        prepareAndSelectNextOrdeal(data, level.getSeed());
    }

    private static int activateMeltdowns(ServerLevel level, FacilityDayData data, int levelNumber) {
        MeltdownCandidatePool pool = collectMeltdownCandidates(level, data,
            MeltdownCause.QLIPHOTH_OVERLOAD);
        List<AbstractAbnormality> candidates = pool.availableTargets();
        if (candidates.isEmpty()) return 0;
        RandomSource random = RandomSource.create(level.getSeed() ^ ((long) data.getDay() << 32) ^ levelNumber);
        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        int requested = MeltdownLifecycle.definition(MeltdownCause.QLIPHOTH_OVERLOAD)
                .requestedTargetCount(pool.statisticalPopulation(), pool.workablePopulation(),
                        levelNumber, Integer.MAX_VALUE);
        if (!CoreSuppressionManager.isSuppressionActive(level)) {
            int maximumActive = meltdownCapacity(level);
            int remainingSlots = maximumActive - data.getActiveMeltdowns().size();
            if (remainingSlots <= 0) return 0;
            requested = Math.min(requested, remainingSlots);
        }
        int count = Math.min(candidates.size(), requested);
        UUID batchId = UUID.randomUUID();
        MeltdownStageContext stageContext = meltdownStageContext(level, data, levelNumber);
        int activated = 0;
        for (int i = 0; i < count; i++) {
            AbstractAbnormality target = candidates.get(i);
            if (MeltdownLifecycle.start(data, target.getUUID(), level.getGameTime(), levelNumber,
                    target.getAbnormalityCode(), MeltdownCause.QLIPHOTH_OVERLOAD,
                    stageContext, batchId, null, "qliphoth_overload",
                    meltdownTargetLocator(target))) {
                activated++;
            }
        }
        return activated;
    }

    /** Non-core days may expose only two more meltdown units than facility players. */
    static int meltdownCapacity(ServerLevel level) {
        return MeltdownPolicy.normalMeltdownCapacity(level.players().size());
    }

    private static void playMeltdownAlarm(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(ModSounds.CORE_MELTDOWN_ALARM.get(),
                    net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    /** Direct Apocalypse Bird egg overloads do not advance the facility gauge. */
    public static int activateEndBirdMeltdowns(ServerLevel level, int maximumTargets) {
        return activateEndBirdMeltdowns(level, maximumTargets, null, "apocalypse_bird_egg");
    }

    public static int activateEndBirdMeltdowns(ServerLevel level, int maximumTargets,
                                                UUID sourceEntity, String sourceType) {
        if (level == null || maximumTargets <= 0 || level.dimension() != ModDimensions.LOBOTO_KEY) return 0;
        if (ExhibitionSafetyService.isEnabled(level.getServer())) return 0;
        FacilityDayData data = FacilityDayData.get(level);
        int previousActiveMeltdowns = data.getActiveMeltdowns().size();
        MeltdownCandidatePool pool = collectMeltdownCandidates(level, data,
            MeltdownCause.APOCALYPSE_BIRD_EGG);
        List<AbstractAbnormality> candidates = pool.availableTargets();
        if (candidates.isEmpty()) return 0;
        Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        int levelNumber = Math.max(1, data.getMeltdownLevel());
        int requested = MeltdownLifecycle.definition(MeltdownCause.APOCALYPSE_BIRD_EGG)
            .requestedTargetCount(pool.statisticalPopulation(), pool.workablePopulation(),
                levelNumber, maximumTargets);
        if (!CoreSuppressionManager.isSuppressionActive(level)) {
            int remainingSlots = meltdownCapacity(level) - data.getActiveMeltdowns().size();
            if (remainingSlots <= 0) return 0;
            requested = Math.min(requested, remainingSlots);
        }
        int count = Math.min(requested, candidates.size());
        UUID batchId = UUID.randomUUID();
        MeltdownStageContext stageContext = meltdownStageContext(level, data, levelNumber);
        int activated = 0;
        for (int i = 0; i < count; i++) {
            AbstractAbnormality target = candidates.get(i);
            if (MeltdownLifecycle.start(data, target.getUUID(), level.getGameTime(), levelNumber,
                    target.getAbnormalityCode(), MeltdownCause.APOCALYPSE_BIRD_EGG,
                    stageContext, batchId, sourceEntity, sourceType,
                    meltdownTargetLocator(target))) {
                activated++;
            }
        }
        if (!MeltdownPolicy.shouldAnnounceActivation(previousActiveMeltdowns, activated)) return 0;
        CrimsonDawnEvent.sendOrdealTitle(level.getServer(), "终末鸟警报",
                "逆卡巴拉熔毁", "鸟蛋使 " + activated + " 个收容单元进入熔毁。", 0xD84A4A);
        playMeltdownAlarm(level.getServer());
        syncAll(level.getServer(), data);
        return activated;
    }

    private static MeltdownCandidatePool collectMeltdownCandidates(ServerLevel level,
                                                                    FacilityDayData data,
                                                                    MeltdownCause cause) {
        List<AbstractAbnormality> available = new ArrayList<>();
        int statisticalPopulation = 0;
        int workablePopulation = 0;
        boolean excludeWorking = MeltdownLifecycle.definition(cause).excludeWorkingTargets();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
                continue;
            }

            // CreatureOverloadManager calculates the requested amount from a
            // broad statistics pool first. Current suppression, escape state,
            // active work and existing overloads are target-pool filters only.
            if (!hasCompletedDepartmentMeltdownImmunity(level, abnormality, cause)) {
                statisticalPopulation++;
                if (isWorkableMeltdownPopulation(abnormality)) workablePopulation++;
            }

            if (abnormality.hasEscape()
                    || !isWorkableMeltdownPopulation(abnormality)
                    || isMeltdownProtected(level, abnormality)
                    || isMeltdownDepartmentProtected(level, abnormality, cause)) continue;
            if ((excludeWorking && WorkManager.isAbnormalityBeingWorked(abnormality))
                    || data.getActiveMeltdowns().containsKey(abnormality.getUUID())) continue;
            available.add(abnormality);
        }
        return new MeltdownCandidatePool(available, statisticalPopulation, workablePopulation);
    }

    private record MeltdownCandidatePool(List<AbstractAbnormality> availableTargets,
                                         int statisticalPopulation,
                                         int workablePopulation) {
    }

    private static boolean isWorkableMeltdownPopulation(AbstractAbnormality abnormality) {
        return MeltdownPolicy.hasNormalWorkCapacity(
                abnormality.getMaxPEOutput(), isEndBirdEncounterEntity(abnormality));
    }

    private static MeltdownStageContext meltdownStageContext(ServerLevel level,
                                                              FacilityDayData data,
                                                              int levelNumber) {
        CoreSuppressionData suppression = CoreSuppressionData.get(level);
        CoreSuppressionType type = suppression.isActive() ? suppression.getActiveType() : null;
        return new MeltdownStageContext(data.getDay(), levelNumber, suppression.isActive(),
            type == null ? "" : type.getId(), -1);
    }

    private static MeltdownTargetLocator meltdownTargetLocator(AbstractAbnormality target) {
        return MeltdownTargetLocator.of(target.getHomeRoomDimension(), target.getHomeRoomMarker());
    }

    private static boolean isEndBirdEncounterEntity(AbstractAbnormality abnormality) {
        return abnormality instanceof EntityEndBird
                || abnormality instanceof EntityEndBirdEggSmall
                || abnormality instanceof EntityEndBirdEggHigh
                || abnormality instanceof EntityEndBirdEggEye;
    }

    private static void failMeltdown(MinecraftServer server, FacilityDayData data, UUID target,
                                     FacilityDayData.ActiveMeltdown meltdown) {
        if (meltdown == null || MeltdownLifecycle.definition(meltdown.cause()).failurePolicy()
                == MeltdownFailurePolicy.NONE) return;
        AbstractAbnormality abnormality = findAbnormality(server, target, meltdown);
        if (abnormality != null && abnormality.level() instanceof ServerLevel level
                && isMeltdownProtected(level, abnormality)) {
            return;
        }
        int penalty = 5 * Math.max(1, meltdown.level());
        data.subtractEnergy(penalty);
        data.notifyState(3);
        if (abnormality != null) {
            int counter = abnormality.getQliphothCounter();
            if (counter > 0) abnormality.decreaseQliphothCounter(counter);
            server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.lobotocraft.meltdown.failed",
                        abnormality.getLocalizedAbnormalityName(), penalty)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    private static AbstractAbnormality findAbnormality(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractAbnormality abnormality) return abnormality;
        }
        CompanyRoomData rooms = CompanyRoomData.get(server.overworld());
        for (ServerLevel level : server.getAllLevels()) {
            for (RoomEntry room : rooms.getRooms(level.dimension().location())) {
                level.getChunkAt(room.pos);
                if (!(level.getBlockEntity(room.pos) instanceof RoomMarkerBlockEntity marker)
                        || !uuid.equals(marker.getBoundAbnormality())) continue;
                Entity entity = level.getEntity(uuid);
                if (entity instanceof AbstractAbnormality abnormality) return abnormality;
            }
        }
        return null;
    }

    private static AbstractAbnormality findAbnormality(MinecraftServer server, UUID uuid,
                                                        FacilityDayData.ActiveMeltdown meltdown) {
        if (meltdown != null && meltdown.targetLocator().isKnown()) {
            net.minecraft.resources.ResourceLocation dimension =
                meltdown.targetLocator().dimension();
            net.minecraft.core.BlockPos markerPos = meltdown.targetLocator().marker();
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (level != null && markerPos != null) {
                level.getChunkAt(markerPos);
                Entity direct = level.getEntity(uuid);
                if (direct instanceof AbstractAbnormality abnormality) return abnormality;
                if (level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker) {
                    UUID bound = marker.getBoundAbnormality();
                    Entity rebound = bound == null ? null : level.getEntity(bound);
                    if (rebound instanceof AbstractAbnormality abnormality) return abnormality;
                }
            }
        }
        return findAbnormality(server, uuid);
    }

    private static boolean removeProtectedMeltdowns(MinecraftServer server, FacilityDayData data) {
        List<UUID> protectedTargets = data.getActiveMeltdowns().entrySet().stream()
            .filter(entry -> {
                if (MeltdownPolicy.isProtectedContainmentCode(entry.getValue().targetCode())) return true;
                AbstractAbnormality abnormality = findAbnormality(server, entry.getKey(),
                    entry.getValue());
                return abnormality != null
                    && abnormality.level() instanceof ServerLevel level
                    && isMeltdownProtected(level, abnormality);
            })
            .map(java.util.Map.Entry::getKey)
            .toList();
        for (UUID target : protectedTargets) {
            MeltdownLifecycle.end(data, target, MeltdownEndReason.RULE_PROTECTED);
        }
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

    private static boolean isMeltdownDepartmentProtected(ServerLevel level,
                                                          AbstractAbnormality abnormality,
                                                          MeltdownCause cause) {
        String departmentId = meltdownDepartmentId(level, abnormality);
        if (departmentId.isBlank()) return false;

        CoreSuppressionData suppression = CoreSuppressionData.get(level);
        CoreSuppressionType activeType = suppression.isActive() ? suppression.getActiveType() : null;
        boolean honorCompletedRewards = cause == MeltdownCause.QLIPHOTH_OVERLOAD;
        return MeltdownDepartmentPolicy.isProtected(departmentId, activeType,
                suppression.getCompletedMask(), honorCompletedRewards);
    }

    private static boolean hasCompletedDepartmentMeltdownImmunity(
            ServerLevel level, AbstractAbnormality abnormality, MeltdownCause cause) {
        String departmentId = meltdownDepartmentId(level, abnormality);
        if (departmentId.isBlank()) return false;

        CoreSuppressionData suppression = CoreSuppressionData.get(level);
        CoreSuppressionType activeType = suppression.isActive() ? suppression.getActiveType() : null;
        boolean honorCompletedRewards = cause == MeltdownCause.QLIPHOTH_OVERLOAD;
        return MeltdownDepartmentPolicy.hasCompletedRewardImmunity(
                departmentId, activeType, suppression.getCompletedMask(), honorCompletedRewards);
    }

    private static String meltdownDepartmentId(ServerLevel level,
                                                AbstractAbnormality abnormality) {
        if (abnormality.getHomeRoomDimension() == null
                || abnormality.getHomeRoomMarker() == null) return "";
        RoomEntry room = CompanyRoomData.get(level).findRoom(
                abnormality.getHomeRoomDimension(), abnormality.getHomeRoomMarker());
        return room == null || room.departmentId == null ? "" : room.departmentId;
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
        Component name = Component.translatable(tier == OrdealTier.DUSK
                ? "ordeal.lobotocraft.dusk.name" : "ordeal.lobotocraft.midnight.name");
        CrimsonDawnEvent.sendOrdealTitle(level.getServer(), name,
                Component.translatable("ordeal.lobotocraft.unfinished.title"),
                Component.translatable("ordeal.lobotocraft.unfinished.subtitle"), 0x888888);
        Component message = Component.translatable("message.lobotocraft.ordeal.unfinished", name)
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
