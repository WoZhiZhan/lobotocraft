package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.lobotocraft.facility.FacilityPauseState;
import com.wzz.lobotocraft.event.listener.BlueMiddayEvent;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.event.definition.work.*;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.item.WorkDeviceItem;
import com.wzz.lobotocraft.item.ego.base.IWorkBonusItem;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenWorkProgressPacket;
import com.wzz.lobotocraft.network.packet.WorkExtractionPacket;
import com.wzz.lobotocraft.network.packet.WorkCompletePacket;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工作管理器 - 带员工属性加成和观察等级加成
 */
@Mod.EventBusSubscriber
public class WorkManager {

    private static final ConcurrentHashMap<UUID, WorkSession> activeWorks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> suppressionWorkCooldowns = new ConcurrentHashMap<>();

    // 基础配置常量
    private static final int BASE_EXTRACTION_INTERVAL = 40;
    private static final int SUPPRESSION_WORK_COOLDOWN_TICKS = 5 * 20;

    private enum SessionState {
        ACTIVE,
        COMPLETING,
        CANCELED,
        COMPLETED
    }

    /**
     * 工作会话数据（含自律加成、观察等级加成和异想体特殊逻辑）
     */
    public static class WorkSession {
        public final ServerPlayer player;
        public final AbstractAbnormality abnormality;
        /** The command slot selected by the player or dispatcher. */
        public final WorkType requestedWorkType;
        /** The server-authoritative work type after Malkuth's mapping is applied. */
        public final WorkType workType;
        public final float baseSuccessRate;
        public final float actualSuccessRate;  // 开始时的展示快照；提取使用 currentSuccessRate()
        private final float rateBeforeAbnormalityModifier;
        public final int baseExtractionInterval;
        public final int extractionInterval;   // 含自律和观察等级速度加成
        public final int temperance;  // 自律值
        public final int observationLevel;  // 观察等级
        public final int itemSpeedBonus;           // 饰品速度加成（tick减少）
        public final float itemSuccessBonus;       // 饰品成功率加成（百分比）
        public final Component itemBonusMessage;   // 饰品加成显示信息
        public final boolean continuousMode;
        /** Snapshot used by the original LC attribute-growth formula. */
        public final float startingHealth;
        public final float startingMental;

        public int workDuration = 0; // 总工作时间
        public int currentTick = 0;
        public int extractionCount = 0;
        public int successCount = 0;
        public int failureCount = 0;
        public boolean forcedEnd = false;  // 是否被强制结束（恐慌）
        public boolean stopContinuousAfterCurrent = false;
        public boolean completeImmediately = false;
        public WorkResult forcedResult = null;
        public String forcedEndReason = "恐慌";
        private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.ACTIVE);

        private float currentSpeedMultiplier = 1.0f;
        private int currentExtractionInterval;

        // 上次计算时间（用于减少频繁计算）
        private int lastMultiplierCalcTick = 0;
        private static final int MULTIPLIER_CALC_INTERVAL = 10; // 每10tick重新计算一次倍率

        private boolean beginCompletion() {
            return state.compareAndSet(SessionState.ACTIVE, SessionState.COMPLETING);
        }

        private boolean cancel() {
            return state.compareAndSet(SessionState.ACTIVE, SessionState.CANCELED);
        }

        private void finishCompletion() {
            state.compareAndSet(SessionState.COMPLETING, SessionState.COMPLETED);
        }

        private boolean isCompleting() {
            return state.get() == SessionState.COMPLETING;
        }

        public WorkSession(ServerPlayer player, AbstractAbnormality abnormality, WorkType workType, boolean continuousMode) {
            this(player, abnormality, workType, workType, continuousMode);
        }

        public WorkSession(ServerPlayer player, AbstractAbnormality abnormality,
                           WorkType requestedWorkType, WorkType workType, boolean continuousMode) {
            this.player = player;
            this.abnormality = abnormality;
            this.requestedWorkType = requestedWorkType;
            this.workType = workType;
            this.continuousMode = continuousMode;
            this.startingHealth = player.getHealth();
            this.startingMental = MentalValueUtil.getMentalValue(player);
            WorkAttributeLevels attributes = player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                    .map(WorkAttributeLevels::from).orElse(WorkAttributeLevels.LOWEST);

            float[][] fullPrefs = abnormality.getFullWorkPreferences();
            int workIndex = workType.ordinal();
            int levelIndex = attributes.columnFor(workType);
            if (fullPrefs != null && workIndex < fullPrefs.length
                    && levelIndex < fullPrefs[workIndex].length) {
                this.baseSuccessRate = fullPrefs[workIndex][levelIndex];
            } else {
                // 降级兜底
                this.baseSuccessRate = abnormality.getWorkPreferences()[workIndex];
            }

            // 获取自律值
            final int[] tempValue = {20};
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                    .ifPresent(stats -> tempValue[0] = stats.getTemperance());
            this.temperance = tempValue[0];

            // 获取观察等级
            final int[] obsLevel = {0};
            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                    .ifPresent(data -> obsLevel[0] = data.getObservationLevel(abnormality.getAbnormalityCode()));
            this.observationLevel = obsLevel[0];

            // 计算自律加成
            float temperanceBonus = (temperance - 20) / 10.0f * 0.01f;

            // 计算观察等级加成
            float observationSuccessBonus = 0.0f;
            int observationSpeedBonus = 0;

            for (int i = 1; i <= observationLevel; i++) {
                IAbnormality.ObservationLevelBonus bonus = abnormality.getObservationBonus(i);
                observationSuccessBonus += bonus.getSuccessRateBonus();
                observationSpeedBonus += bonus.getSpeedBonus();
            }

            this.itemSpeedBonus = calculateItemSpeedBonus(player, workType);
            this.itemSuccessBonus = calculateItemSuccessBonus(player, workType, abnormality);

            // 显示饰品加成信息（在构造函数中准备，外部调用时显示）
            if (itemSpeedBonus > 0 && itemSuccessBonus > 0) {
                this.itemBonusMessage = Component.translatable(
                        "message.lobotocraft.work.item_bonus.both", itemSpeedBonus,
                        formatOneDecimal(itemSuccessBonus * 100));
            } else if (itemSpeedBonus > 0) {
                this.itemBonusMessage = Component.translatable(
                        "message.lobotocraft.work.item_bonus.speed", itemSpeedBonus);
            } else if (itemSuccessBonus > 0) {
                this.itemBonusMessage = Component.translatable(
                        "message.lobotocraft.work.item_bonus.success",
                        formatOneDecimal(itemSuccessBonus * 100));
            } else {
                this.itemBonusMessage = Component.empty();
            }

            // 应用所有加成的计算
            float rateWithBonuses = Math.min(0.95f,
                    baseSuccessRate + temperanceBonus + observationSuccessBonus + itemSuccessBonus
                            + CoreSuppressionManager.getWorkSuccessBonus(player));

            // 应用速度加成
            int intervalAfterTemperance = (int)(BASE_EXTRACTION_INTERVAL * (1.0f - temperanceBonus));
            int intervalWithObservation = Math.max(20, intervalAfterTemperance - observationSpeedBonus);

            // 最终应用饰品速度加成（确保最小间隔）
            this.extractionInterval = Math.max(10, intervalWithObservation - itemSpeedBonus);
            this.baseExtractionInterval = Math.max(10, intervalWithObservation - itemSpeedBonus);
            this.currentExtractionInterval = this.baseExtractionInterval;
            // 让异想体有机会修改成功率（优先级最高）
            this.rateBeforeAbnormalityModifier = rateWithBonuses;
            this.actualSuccessRate = currentSuccessRate();
        }

        /** Re-evaluate the existing live modifier, always from the unmodified input. */
        public float currentSuccessRate() {
            Float modified = abnormality.modifyWorkSuccessRate(player, workType, rateBeforeAbnormalityModifier);
            return Objects.requireNonNullElse(modified, rateBeforeAbnormalityModifier);
        }

        /**
         * 更新速度倍率和当前间隔（需要在每tick调用）
         */
        public void updateSpeedMultiplier(int currentTickCount) {
            // 避免每tick都计算，减少性能开销
            if (currentTickCount - lastMultiplierCalcTick < MULTIPLIER_CALC_INTERVAL) {
                return;
            }
            lastMultiplierCalcTick = currentTickCount;

            float newMultiplier = WorkSpeedModifierManager.calculateFinalMultiplier(player, this);

            // 只在倍率变化时更新间隔
            if (Math.abs(newMultiplier - currentSpeedMultiplier) > 0.01f) {
                currentSpeedMultiplier = newMultiplier;
                int newInterval = (int)(baseExtractionInterval / currentSpeedMultiplier);
                currentExtractionInterval = Math.max(5, newInterval); // 最小间隔5tick
            }
        }

        public boolean isComplete() {
            return extractionCount >= abnormality.getMaxPEOutput() || forcedEnd;
        }

        public float getProgress() {
            return (float) extractionCount / abnormality.getMaxPEOutput();
        }

        public Component getFormattedDuration() {
            long seconds = workDuration / 20;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            if (minutes > 0) {
                return Component.translatable("message.lobotocraft.work.duration.minutes",
                        minutes, seconds);
            }
            return Component.translatable("message.lobotocraft.work.duration.seconds", seconds);
        }
    }

    public static boolean isPlayerWorking(ServerPlayer player) {
        return activeWorks.containsKey(player.getUUID());
    }

    public static boolean isAbnormalityBeingWorked(AbstractAbnormality abnormality) {
        UUID abnormalityUuid = abnormality.getUUID();
        return activeWorks.values().stream()
                .anyMatch(session -> session.abnormality.getUUID().equals(abnormalityUuid));
    }

    /**
     * AI reservation is intentionally narrower than the list of all active
     * work sessions. Human players never reserve an abnormality against other
     * players or against an AI employee.
     */
    public static boolean isAiEmployeeWorking(AbstractAbnormality abnormality) {
        if (abnormality == null) return false;
        UUID abnormalityUuid = abnormality.getUUID();
        return activeWorks.values().stream()
                .anyMatch(session -> AbnormalityWorkRules.isAiEmployee(session.player)
                        && session.abnormality.getUUID().equals(abnormalityUuid));
    }

    private static Component getSuppressionWorkDenial(ServerPlayer player, AbstractAbnormality abnormality) {
        if (!CoreSuppressionManager.isSuppressionActive(player.level())) return null;
        if (AbnormalityWorkRules.hasAiReservationConflict(
                AbnormalityWorkRules.isAiEmployee(player), isAiEmployeeWorking(abnormality))) {
            return Component.translatable("message.lobotocraft.work.suppression_multi_denied");
        }
        long gameTime = player.serverLevel().getGameTime();
        long cooldownEnd = suppressionWorkCooldowns.getOrDefault(abnormality.getUUID(), 0L);
        if (cooldownEnd <= gameTime) {
            suppressionWorkCooldowns.remove(abnormality.getUUID(), cooldownEnd);
            return null;
        }
        long remainingSeconds = (cooldownEnd - gameTime + 19L) / 20L;
        return Component.translatable("message.lobotocraft.work.suppression_cooldown",
                remainingSeconds);
    }

    private static void beginSuppressionWorkCooldown(AbstractAbnormality abnormality) {
        if (!(abnormality.level() instanceof ServerLevel level)
                || !CoreSuppressionManager.isSuppressionActive(level)) {
            return;
        }
        suppressionWorkCooldowns.put(abnormality.getUUID(),
                level.getGameTime() + SUPPRESSION_WORK_COOLDOWN_TICKS);
    }

    public static boolean startWork(ServerPlayer player, AbstractAbnormality abnormality, WorkType workType) {
        return startWork(player, abnormality, workType, WorkDeviceItem.hasEnabledDevice(player), true);
    }

    /** Starts an AI order whose Malkuth mapping was fixed when the order was issued. */
    public static boolean startResolvedWork(ServerPlayer player, AbstractAbnormality abnormality,
                                            WorkType requestedWorkType, WorkType resolvedWorkType) {
        return startWork(player, abnormality, requestedWorkType, resolvedWorkType,
                WorkDeviceItem.hasEnabledDevice(player), true);
    }

    private static boolean startWork(ServerPlayer player, AbstractAbnormality entity, WorkType workType,
                                     boolean continuousMode, boolean openClientScreen) {
        return startWork(player, entity, workType, null, continuousMode, openClientScreen);
    }

    private static boolean startWork(ServerPlayer player, AbstractAbnormality entity,
                                     WorkType requestedWorkType, WorkType resolvedWorkType,
                                     boolean continuousMode, boolean openClientScreen) {
        if (UniqueWorkRequest.isBusy(entity)
                || AbnormalityCommandPolicy.action(entity.getAbnormalityCode(),
                        requestedWorkType.ordinal()) != null) {
            player.sendSystemMessage(Component.translatable("message.lobotocraft.unique_work.use_command"));
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.abnormality_missing"));
            return false;
        }
        String registrationDenial = AbnormalityWorkRules.registrationDenial(entity);
        if (registrationDenial != null) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.registration_denied"));
            return false;
        }
        if (isPlayerWorking(player)) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.already_working"));
            return false;
        }
        Component controlStateDenial = AbnormalityWorkRules.controlStateDenial(player);
        if (controlStateDenial != null) {
            player.sendSystemMessage(controlStateDenial.copy().withStyle(ChatFormatting.RED));
            return false;
        }
        if (AbnormalityWorkRules.hasAiReservationConflict(
                AbnormalityWorkRules.isAiEmployee(player), isAiEmployeeWorking(entity))) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.ai_reserved"));
            return false;
        }
        if (player.distanceToSqr(entity) > 100.0) {
            player.sendSystemMessage(Component.translatable("message.lobotocraft.work.too_far"));
            return false;
        }
        Component suppressionDenial = getSuppressionWorkDenial(player, entity);
        if (suppressionDenial != null) {
            player.sendSystemMessage(suppressionDenial.copy().withStyle(ChatFormatting.RED));
            return false;
        }

        WorkType workType = resolvedWorkType == null
                ? CoreSuppressionManager.randomizeWorkType(player, requestedWorkType)
                : resolvedWorkType;

        if (MentalValueUtil.isPanic(player)) {
            player.sendSystemMessage(Component.translatable("message.lobotocraft.work.panic"));
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.panic_recovery"));
            return false;
        }

        // 检查员工等级是否满足要求
        int requiredLevel = entity.getRequiredEmployeeLevel();
        final int[] playerLevel = {1};

        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            playerLevel[0] = stats.getEmployeeLevel();
        });

        if (playerLevel[0] < requiredLevel) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.level_insufficient", requiredLevel, playerLevel[0]));
            return false;
        }
        WorkStartEvent startEvent = new WorkStartEvent(player, entity, workType);
        if (MinecraftForge.EVENT_BUS.post(startEvent)) {
            // 事件被取消
            if (!startEvent.getCancelReason().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c" + startEvent.getCancelReason()));
            }
            return false;
        }
        if (isPlayerWorking(player) || !isStartContextValid(player, entity)) return false;
        // 调用异想体的工作开始回调
        if (!entity.onWorkStart(player, workType)) {
            // 异想体阻止了工作开始
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.cannot_start"));
            return false;
        }
        if (isPlayerWorking(player) || !isStartContextValid(player, entity)) {
            entity.onWorkInterrupted(player, workType, "Work start context changed");
            return false;
        }

        WorkSession session = new WorkSession(
                player, entity, requestedWorkType, workType, continuousMode);
        if (activeWorks.putIfAbsent(player.getUUID(), session) != null) return false;
        if (!validateAfterCallback(session)) return false;

        // 显示自律加成信息
        if (session.temperance > 20) {
            float bonus = (session.temperance - 20) / 10.0f;
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.temperance_bonus",
                    formatOneDecimal(bonus), formatOneDecimal(bonus)));
        }

        // 显示观察等级加成信息
        if (session.observationLevel > 0) {
            float totalSuccessBonus = 0.0f;
            int totalSpeedBonus = 0;

            for (int i = 1; i <= session.observationLevel; i++) {
                IAbnormality.ObservationLevelBonus bonus = entity.getObservationBonus(i);
                totalSuccessBonus += bonus.getSuccessRateBonus();
                totalSpeedBonus += bonus.getSpeedBonus();
            }

            String key = totalSuccessBonus > 0 && totalSpeedBonus > 0
                    ? "message.lobotocraft.work.observation_bonus.both"
                    : totalSuccessBonus > 0
                    ? "message.lobotocraft.work.observation_bonus.success"
                    : "message.lobotocraft.work.observation_bonus.speed";
            if (totalSuccessBonus > 0 && totalSpeedBonus > 0) {
                player.sendSystemMessage(Component.translatable(key, session.observationLevel,
                        Math.round(totalSuccessBonus * 100), totalSpeedBonus));
            } else if (totalSuccessBonus > 0) {
                player.sendSystemMessage(Component.translatable(key, session.observationLevel,
                        Math.round(totalSuccessBonus * 100)));
            } else if (totalSpeedBonus > 0) {
                player.sendSystemMessage(Component.translatable(key, session.observationLevel,
                        totalSpeedBonus));
            }
        }

        boolean malkuthScramble = CoreSuppressionManager.isMalkuthWorkScrambled(player.level());
        if (!malkuthScramble && !session.itemBonusMessage.getString().isEmpty()) {
            player.sendSystemMessage(session.itemBonusMessage);
        }

        if (!malkuthScramble) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.interval", session.extractionInterval,
                    BASE_EXTRACTION_INTERVAL));
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.success_rate",
                    formatOneDecimal(session.actualSuccessRate * 100),
                    formatOneDecimal(session.baseSuccessRate * 100)));
        }

        player.sendSystemMessage(Component.translatable("message.lobotocraft.work.started",
                malkuthScramble ? Component.literal("???")
                        : Component.translatable(workType.getTranslationKey())));

        if (continuousMode) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.continuous_enabled"));
        }

        if (openClientScreen) {
            MessageLoader.getLoader().sendToPlayer(player,
                    new OpenWorkProgressPacket(
                            entity.getEntityId(),
                            requestedWorkType,
                            malkuthScramble ? -1.0F : session.actualSuccessRate,
                            entity.getMaxPEOutput(),
                            entity.getEntityId(),
                            session.observationLevel,
                            continuousMode
                    )
            );
        }
        return true;
    }

    public static void cancelWork(ServerPlayer player, String reason) {
        cancelWork(activeWorks.get(player.getUUID()), reason, true);
    }

    private static void cancelWork(WorkSession session, String reason, boolean notifyPlayer) {
        if (session == null || !activeWorks.remove(session.player.getUUID(), session) || !session.cancel()) return;
        ServerPlayer player = session.player;
        try {
            MinecraftForge.EVENT_BUS.post(new WorkInterruptEvent(
                    player,
                    session.abnormality,
                    session.workType,
                    reason,
                    session
            ));
            // 调用异想体的工作中断回调
            session.abnormality.onWorkInterrupted(player, session.workType, reason);
        } catch (Exception exception) {
            ModLogger.getLogger().error("Work interruption callback failed", exception);
        }
        if (notifyPlayer) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.interrupted", displayReason(reason)));
            player.closeContainer();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Snapshot iteration allows cancellation callbacks to manipulate the
        // live map safely.  In particular, never call Iterator.remove after
        // cancelWork: a listener can legally issue a replacement order for
        // the same UUID, and an unconditional iterator removal would delete
        // that replacement session.
        for (WorkSession session : new ArrayList<>(activeWorks.values())) {
            // A callback earlier in this server tick can replace this
            // player's session. Never let the stale snapshot keep extracting
            // PE or apply failure damage after the replacement has won.
            if (activeWorks.get(session.player.getUUID()) != session) continue;

            if (session.player instanceof EmployeeServerPlayer
                    && FacilityPauseState.isPaused(event.getServer())) continue;

            if (!session.player.isAlive() || session.player.isRemoved() || session.player.isDeadOrDying()) {
                cancelWork(session, "死亡中断对异想体的工作！", true);
                continue;
            }

            if (!session.abnormality.isAlive() || session.abnormality.isRemoved()
                    || session.abnormality.level() != session.player.level()) {
                cancelWork(session, "Invalid work target", true);
                continue;
            }

            if (session.player.distanceToSqr((Entity) session.abnormality) > 100.0) {
                cancelWork(session, "你离异想体太远了", true);
                continue;
            }

            // 检查玩家是否恐慌
            boolean isPanic = checkPlayerPanic(session.player);
            if (isPanic) {
                cancelWork(session, "Work interrupted by panic", true);
                continue;
            }
            if (session.completeImmediately) {
                if (!claimForCompletion(session)) continue;
                boolean restartContinuous = completeWork(session);
                if (restartContinuous) {
                    startWork(session.player, session.abnormality, session.requestedWorkType, true, true);
                }
                continue;
            }
            if (session.forcedEnd) {
                cancelWork(session, session.forcedEndReason, true);
                continue;
            }
            if (session.continuousMode && !session.stopContinuousAfterCurrent) {
                Component stopReason = getContinuousStopReason(session);
                if (stopReason != null) {
                    markContinuousStop(session, stopReason);
                }
            }

            // 更新速度倍率（每tick调用，内部有缓存机制）
            session.updateSpeedMultiplier(session.workDuration);

            session.currentTick++;
            session.workDuration++;

            session.abnormality.onWorkTick(session.player, session, session.workType);
            if (!validateAfterCallback(session)) continue;
            if (session.completeImmediately) {
                if (!claimForCompletion(session)) continue;
                boolean restartContinuous = completeWork(session);
                if (restartContinuous) {
                    startWork(session.player, session.abnormality, session.requestedWorkType, true, true);
                }
                continue;
            }
            MinecraftForge.EVENT_BUS.post(new WorkTickEvent(
                    session.player,
                    session.abnormality,
                    session.workType,
                    session.currentTick,
                    session.currentExtractionInterval,  // 使用动态间隔
                    session
            ));
            if (!validateAfterCallback(session)) continue;

            if (session.currentTick >= session.currentExtractionInterval) {
                session.currentTick = 0;
                if (!performExtraction(session)) continue;
                if (session.isComplete()) {
                    if (!claimForCompletion(session)) continue;
                    boolean restartContinuous = completeWork(session);
                    if (restartContinuous) {
                        startWork(session.player, session.abnormality, session.requestedWorkType, true, true);
                    }
                }
            }
        }
    }

    private static boolean checkPlayerPanic(ServerPlayer player) {
        return MentalValueUtil.isPanic(player);
    }

    public static void requestStopContinuousWork(ServerPlayer player, String reason) {
        requestStopContinuousWork(player, Component.literal(reason));
    }

    public static void requestStopContinuousWork(ServerPlayer player, Component reason) {
        WorkDeviceItem.disableAll(player);
        WorkSession session = activeWorks.get(player.getUUID());
        if (session != null && session.continuousMode) {
            markContinuousStop(session, reason);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.continuous_stopped"));
        }
    }

    private static void markContinuousStop(WorkSession session, String reason) {
        markContinuousStop(session, Component.literal(reason));
    }

    private static void markContinuousStop(WorkSession session, Component reason) {
        if (session.stopContinuousAfterCurrent) return;
        session.stopContinuousAfterCurrent = true;
        WorkDeviceItem.disableAll(session.player);
        session.player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.work.continuous_stopping_reason", reason));
    }

    private static boolean shouldRestartContinuous(WorkSession session) {
        if (!session.continuousMode || session.forcedEnd || session.stopContinuousAfterCurrent) {
            return false;
        }
        if (!WorkDeviceItem.hasEnabledDevice(session.player)) {
            return false;
        }
        Component stopReason = getContinuousStopReason(session);
        if (stopReason != null) {
            markContinuousStop(session, stopReason);
            return false;
        }
        return true;
    }

    private static Component getContinuousStopReason(WorkSession session) {
        ServerPlayer player = session.player;
        if (isOrdealActive(player)) {
            return Component.translatable("message.lobotocraft.work.stop_reason.ordeal");
        }
        if (isInventoryFull(player)) {
            return Component.translatable("message.lobotocraft.work.stop_reason.inventory_full");
        }
        if (player.getHealth() <= player.getMaxHealth() * 0.2f) {
            return Component.translatable("message.lobotocraft.work.stop_reason.low_health");
        }
        float maxMental = MentalValueUtil.getEffectiveMaxMentalValue(player);
        if (maxMental > 0 && MentalValueUtil.getMentalValue(player) <= maxMental * 0.2f) {
            return Component.translatable("message.lobotocraft.work.stop_reason.low_mental");
        }
        return null;
    }

    private static boolean isOrdealActive(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        return OrdealData.get(level).hasActiveOrdeal() || BlueMiddayEvent.isTrialActive();
    }

    private static boolean isInventoryFull(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean performExtraction(WorkSession session) {
        ServerPlayer player = session.player;
        AbstractAbnormality abnormality = session.abnormality;

        WorkPreExtractionEvent preEvent = new WorkPreExtractionEvent(
                player, abnormality, session.workType,
                session.extractionCount + 1,  // 下一次提取的序号
                session.currentSuccessRate(),
                session
        );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            // 提取被取消
            if (!preEvent.getCancelReason().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c" + preEvent.getCancelReason()));
            }
            return isActive(session);
        }
        if (!validateAfterCallback(session)) return false;

        // 使用事件中可能被修改的成功率
        float effectiveSuccessRate = preEvent.getSuccessRate();
        boolean success = Math.random() < effectiveSuccessRate;

        if (success) {
            session.successCount++;
        } else {
            session.failureCount++;

            WorkDamageEvent damageEvent = new WorkDamageEvent(
                    player, abnormality, session.workType,
                    session.extractionCount + 1, session
            );

            if (!MinecraftForge.EVENT_BUS.post(damageEvent)) {
                applyDamage(player, abnormality, session.workType);
            } else {
                if (!damageEvent.getCancelMessage().isEmpty()) {
                    player.sendSystemMessage(Component.literal(damageEvent.getCancelMessage()));
                }
            }
            if (!validateAfterCallback(session)) return false;
        }

        session.extractionCount++;

        MinecraftForge.EVENT_BUS.post(new WorkExtractionEvent(
                player, abnormality, session.workType,
                success, session.extractionCount, session
        ));
        if (!validateAfterCallback(session)) return false;

        MessageLoader.getLoader().sendToPlayer(player,
                new WorkExtractionPacket(
                        abnormality.getEntityId(),
                        session.requestedWorkType,
                        success,
                        session.extractionCount,
                        session.successCount,
                        session.failureCount,
                        session.currentSpeedMultiplier
                )
        );
        return isActive(session);
    }

    private static void applyDamage(ServerPlayer player, IAbnormality abnormality, WorkType workType) {
        abnormality.attackPlayerOnFailure(player, workType);
    }

    private static boolean completeWork(WorkSession session) {
        ServerPlayer player = session.player;
        IAbnormality abnormality = session.abnormality;
        WorkType workType = session.workType;

        WorkResult result;

        if (session.forcedResult != null) {
            result = session.forcedResult;
        } else if (abnormality.shouldForceWorkResult(player, workType)) {
            // 异想体强制指定结果（如快乐泰迪的第二次沟通）
            result = abnormality.getForcedWorkResult(player, workType);
        } else {
            // 正常流程：根据PE-BOX产量判定结果
            result = abnormality.getWorkResultForPEOutput(session.successCount);
        }

        int peOutput = session.successCount;
        MinecraftForge.EVENT_BUS.post(new WorkCompleteEvent(
                player,
                abnormality,
                workType,
                result,
                peOutput,
                session,
                session.forcedEnd
        ));
        if (!isCompletionValid(session)) return finishCompletion(session, false);
        if (!session.forcedEnd) {
            beginSuppressionWorkCooldown(session.abnormality);
        }
        if (abnormality.shouldGivePEBox(player, workType, result, peOutput)) {
            givePEBox(player, abnormality, workType, result, peOutput);
            int independentBonus = CoreSuppressionManager.getIndependentPeBoxBonus(player, peOutput);
            if (independentBonus > 0) {
                givePEBox(player, abnormality, workType, result, independentBonus);
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.work.yesod_bonus", independentBonus));
            }
        }
        if (!isCompletionValid(session)) return finishCompletion(session, false);
        giveGift(player, abnormality);
        if (session.continuousMode && !session.stopContinuousAfterCurrent) {
            Component stopReason = getContinuousStopReason(session);
            if (stopReason != null) {
                markContinuousStop(session, stopReason);
            }
        }
        handleWorkResult(player, abnormality, workType, result);
        if (!isCompletionValid(session)) return finishCompletion(session, false);
        applyAttributeGain(player, abnormality, workType, session);
        abnormality.onWorkComplete(player, workType, result, session);
        if (!isCompletionValid(session)) return finishCompletion(session, false);
        if (session.continuousMode && !session.stopContinuousAfterCurrent) {
            Component stopReason = getContinuousStopReason(session);
            if (stopReason != null) {
                markContinuousStop(session, stopReason);
            }
        }
        if (player.level().dimension() == ModDimensions.LOBOTO_KEY) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                data.addWorkCount();
                boolean doubled = CoreSuppressionManager.shouldDoubleWorkCount(player);
                if (doubled) data.addWorkCount();
                data.setHasSleep(false);
                int current = data.getTodayWorkCount();
                int required = data.getRequiredWorkCount();
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.work.daily_progress",
                        doubled ? 2 : 1, current, required));

                if (data.hasCompletedTodayWork()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.lobotocraft.work.daily_complete"));
                }
                MessageLoader.getLoader().sendToPlayer(player,
                        new com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket(
                                data.getCurrentDay(),
                                data.getTodayWorkCount(),
                                data.isArmorLocked(),
                                false
                        )
                );
            });
        }
        MessageLoader.getLoader().sendToPlayer(player,
                new WorkCompletePacket(
                        abnormality.getEntityId(),
                        session.requestedWorkType,
                        result,
                        peOutput,
                        session.successCount,
                        session.failureCount
                )
        );
        Component resultText = switch (result) {
            case GOOD -> Component.translatable(result.getTranslationKey())
                    .withStyle(ChatFormatting.GREEN);
            case NORMAL -> Component.translatable(result.getTranslationKey())
                    .withStyle(ChatFormatting.YELLOW);
            case BAD -> Component.translatable(result.getTranslationKey())
                    .withStyle(ChatFormatting.RED);
        };
        if (session.forcedEnd) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.completed_interrupted",
                    displayReason(session.forcedEndReason), session.getFormattedDuration(),
                    resultText, peOutput));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.work.completed", session.getFormattedDuration(),
                    resultText, peOutput));
        }
        return finishCompletion(session, shouldRestartContinuous(session));
    }

    private static boolean isCompletionValid(WorkSession session) {
        // Distance and same-dimension checks belong to work start / extraction only. A result
        // callback may legitimately make the abnormality escape, teleport or replace itself.
        // Rejecting completion after that callback skips WorkCompletePacket and strands the
        // client on WorkProgressScreen forever.
        ServerPlayer player = session.player;
        return session.isCompleting() && player != null && player.isAlive() && !player.isRemoved();
    }

    private static boolean finishCompletion(WorkSession session, boolean restart) {
        session.finishCompletion();
        return restart;
    }

    private static Component displayReason(String reason) {
        if (reason == null || reason.isBlank()) return Component.empty();
        String key = switch (reason) {
            case "死亡中断对异想体的工作！" -> "message.lobotocraft.work.reason.death";
            case "Invalid work target" -> "message.lobotocraft.work.reason.invalid_target";
            case "你离异想体太远了" -> "message.lobotocraft.work.reason.too_far";
            case "Work interrupted by panic", "恐慌" -> "message.lobotocraft.work.reason.panic";
            case "Work context changed during callback" ->
                    "message.lobotocraft.work.reason.context_changed";
            case "Player disconnected" -> "message.lobotocraft.work.reason.disconnected";
            case "调试命令取消工作" -> "message.lobotocraft.work.reason.debug_cancel";
            case "被冰雪女皇冰封，工作中断" -> "message.lobotocraft.work.reason.frozen";
            case "被狼吞入，工作中断" -> "message.lobotocraft.work.reason.wolf_swallowed";
            case "被红舞鞋控制，工作中断" -> "message.lobotocraft.work.reason.red_shoes";
            case "展会停止工作" -> "message.lobotocraft.work.reason.exhibition_stop";
            default -> null;
        };
        return key == null ? Component.literal(reason) : Component.translatable(key);
    }

    private static void sendAttributeGain(ServerPlayer player, String attribute, int added,
                                          String level, int current) {
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.work.attribute_gain." + attribute,
                added, level, current, EmployeeStats.MAX_STAT));
    }

    /**
     * 提升员工属性
     */
    private static void applyAttributeGain(ServerPlayer player, IAbnormality abnormality, WorkType workType,
                                            WorkSession session) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int increase = WorkAttributeExperience.calculateIncrease(
                    player, stats, abnormality.getRiskLevel().name(), workType, session);
            if (increase > 0) {
                switch (workType) {
                    case INSTINCT -> {
                        int added = stats.addFortitude(increase);
                        if (added > 0) {
                            sendAttributeGain(player, "fortitude", added,
                                    EmployeeLevelCalculator.statGrade(stats.getFortitude()),
                                    stats.getFortitude());
                            EmployeeStatsApplier.applyAllAttributes(player);
                        }
                    }
                    case INSIGHT -> {
                        int added = stats.addPrudence(increase);
                        if (added > 0) {
                            sendAttributeGain(player, "prudence", added,
                                    EmployeeLevelCalculator.statGrade(stats.getPrudence()),
                                    stats.getPrudence());
                            EmployeeStatsApplier.applyAllAttributes(player);
                        }
                    }
                    case ATTACHMENT -> {
                        int added = stats.addTemperance(increase);
                        if (added > 0) {
                            sendAttributeGain(player, "temperance", added,
                                    EmployeeLevelCalculator.statGrade(stats.getTemperance()),
                                    stats.getTemperance());
                            EmployeeStatsApplier.applyAllAttributes(player);
                        }
                    }
                    case REPRESSION -> {
                        int added = stats.addJustice(increase);
                        if (added > 0) {
                            sendAttributeGain(player, "justice", added,
                                    EmployeeLevelCalculator.statGrade(stats.getJustice()),
                                    stats.getJustice());
                            EmployeeStatsApplier.applyAllAttributes(player);
                        }
                    }
                }
                MessageLoader.getLoader().sendToPlayer(player,
                        new com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket(
                                stats.getFortitude(),
                                stats.getPrudence(),
                                stats.getTemperance(),
                                stats.getJustice()
                        )
                );
            }
        });
    }

    private static void givePEBox(ServerPlayer player, IAbnormality abnormality,
                                  WorkType workType, WorkResult result, int amount) {
        if (amount <= 0) return;
        ItemStack peBox = PEBoxItem.create(
                abnormality.getAbnormalityCode(),
                abnormality.getAbnormalityName(),
                abnormality instanceof Entity entity ? entity.getType().getDescriptionId() : ""
        );
        peBox.setCount(amount);
        if (!player.addItem(peBox)) {
            player.drop(peBox, false);
        }
    }

    private static boolean isStartContextValid(ServerPlayer player, AbstractAbnormality abnormality) {
        return player != null && player.isAlive() && !player.isRemoved()
                && abnormality != null && abnormality.isAlive() && !abnormality.isRemoved()
                && player.level() == abnormality.level() && player.distanceToSqr(abnormality) <= 100.0;
    }

    private static boolean isActive(WorkSession session) {
        return session != null && session.state.get() == SessionState.ACTIVE
                && activeWorks.get(session.player.getUUID()) == session;
    }

    private static boolean validateAfterCallback(WorkSession session) {
        if (!isActive(session)) return false;
        if (isStartContextValid(session.player, session.abnormality)) return true;
        cancelWork(session, "Work context changed during callback", true);
        return false;
    }

    private static boolean claimForCompletion(WorkSession session) {
        if (!isActive(session) || !activeWorks.remove(session.player.getUUID(), session)) return false;
        return session.beginCompletion();
    }

    /** Gifts are rolled exclusively after a completed server-side work session. */
    private static void giveGift(ServerPlayer player, IAbnormality abnormality) {
        var gift = abnormality.getEGOGiftData();
        if (gift == null) return;
        float probability = abnormality.getGiftProbability();
        if (!(probability > 0.0F) || player.getRandom().nextFloat() >= Math.min(1.0F, probability)) return;
        ItemStack giftStack = com.wzz.lobotocraft.entity.data.EGOEquipmentData.stack(gift.itemId());
        if (giftStack.isEmpty()) return;
        if (!player.addItem(giftStack)) player.drop(giftStack, false);
        player.sendSystemMessage(Component.translatable("message.lobotocraft.work.gift_obtained_simple",
                giftStack.getHoverName()));
    }

    private static String formatOneDecimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static void handleWorkResult(ServerPlayer player, IAbnormality abnormality,
                                         WorkType workType, WorkResult result) {
        switch (result) {
            case GOOD -> abnormality.onGoodWork(player);
            case NORMAL -> abnormality.onNormalWork(player);
            case BAD -> {
                abnormality.onBadWork(player);
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.work.bad_result"));
            }
        }
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    private static int calculateItemSpeedBonus(ServerPlayer player, WorkType workType) {
        AtomicInteger totalBonus = new AtomicInteger();
        CuriosApi.getCuriosHelper().getEquippedCurios(player).ifPresent(curios -> {
            for (int i = 0; i < curios.getSlots(); i++) {
                ItemStack stack = curios.getStackInSlot(i);
                if (stack.getItem() instanceof IWorkBonusItem bonusItem) {
                    totalBonus.addAndGet(bonusItem.getWorkSpeedBonus(player, workType));
                }
            }
        });
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (armorStack.getItem() instanceof IWorkBonusItem bonusItem) {
                totalBonus.addAndGet(bonusItem.getWorkSpeedBonus(player, workType));
            }
        }
        return totalBonus.get();
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    private static float calculateItemSuccessBonus(ServerPlayer player, WorkType workType, IAbnormality abnormality) {
        AtomicReference<Float> totalBonus = new AtomicReference<>(0.0f);
        CuriosApi.getCuriosHelper().getEquippedCurios(player).ifPresent(curios -> {
            for (int i = 0; i < curios.getSlots(); i++) {
                ItemStack stack = curios.getStackInSlot(i);
                if (stack.getItem() instanceof IWorkBonusItem bonusItem) {
                    totalBonus.updateAndGet(v -> v + bonusItem.getWorkSuccessBonus(player, workType));
                    totalBonus.updateAndGet(v -> v + bonusItem.getRiskSpecificBonus(abnormality.getRiskLevel().name()));
                }
            }
        });
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (armorStack.getItem() instanceof IWorkBonusItem bonusItem) {
                totalBonus.updateAndGet(v -> v + bonusItem.getWorkSuccessBonus(player, workType));
                totalBonus.updateAndGet(v -> v + bonusItem.getRiskSpecificBonus(abnormality.getRiskLevel().name()));
            }
        }
        return totalBonus.get();
    }

    /**
     * 获取玩家的工作会话
     * @param player 玩家
     * @return 工作会话，如果玩家没有工作则返回null
     */
    public static WorkSession getWorkSession(ServerPlayer player) {
        return activeWorks.get(player.getUUID());
    }

    public static void forceCompleteWork(ServerPlayer player, WorkResult result, String reason) {
        WorkSession session = activeWorks.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.forcedEnd = true;
        session.forcedEndReason = reason;
        session.forcedResult = result;
        session.completeImmediately = true;
        session.stopContinuousAfterCurrent = true;
        WorkDeviceItem.disableAll(player);
    }

    /**
     * 获取玩家正在工作的异想体
     * @param player 玩家
     * @return 异想体，如果玩家没有工作则返回null
     */
    public static IAbnormality getWorkingAbnormality(ServerPlayer player) {
        WorkSession session = activeWorks.get(player.getUUID());
        return session != null ? session.abnormality : null;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancelWork(activeWorks.get(player.getUUID()), "Player disconnected", false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (WorkSession session : activeWorks.values()) session.cancel();
        activeWorks.clear();
        suppressionWorkCooldowns.clear();
    }
}
