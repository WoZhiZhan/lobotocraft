package com.wzz.lobotocraft.core_suppression;

import com.lobotocraft.facility.day.DayTransitionService;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyData;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.command.KillCommand;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.event.definition.work.WorkDamageEvent;
import com.wzz.lobotocraft.event.listener.BlueMiddayEvent;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.WorkDeviceItem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionSyncPacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionWarningPacket;
import com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket;
import com.wzz.lobotocraft.network.packet.OrdealTitlePacket;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import com.wzz.lobotocraft.world.structure.Structures;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class CoreSuppressionManager {
    public static final int REQUIRED_DAY = 26;
    public static final int REQUIRED_FULL_OBSERVATIONS = 26;
    public static final int REQUIRED_COMPANY_ABNORMALITIES = 26;
    /** Legacy ordeal telemetry used by debug tooling; no longer a completion requirement. */
    @Deprecated public static final int REQUIRED_DAWNS = 2;
    /** Legacy ordeal telemetry used by debug tooling; no longer a completion requirement. */
    @Deprecated public static final int REQUIRED_MIDDAYS = 1;
    public static final int MELTDOWN_WORK_INTERVAL = 10;
    public static final int MELTDOWN_DURATION_TICKS = 60 * 20;
    public static final int MELTDOWN_WORK_PENALTY = 5;
    public static final float MALKUTH_COMPLETION_SUCCESS_BONUS = 0.10F;
    public static final int VISUAL_WARNING_DURATION_TICKS = 60 * 20;
    private static final String STOP_SUPPRESSION_COMMAND = "/lobotocraft debug suppression end false";

    private static final String RETURN_X = "lobotocraft:LotoOldX";
    private static final String RETURN_Y = "lobotocraft:LotoOldY";
    private static final String RETURN_Z = "lobotocraft:LotoOldZ";
    private static final String RETURN_LEVEL = "lobotocraft:LotoOldLevelName";
    private static final String MELTDOWN_ACTIVE = "lobotocraft:CoreMeltdownActive";
    private static final String MELTDOWN_DEADLINE = "lobotocraft:CoreMeltdownDeadline";
    private static final String MELTDOWN_PLAYER = "lobotocraft:CoreMeltdownPlayer";
    private static final String MELTDOWN_PREVIOUS_TEAM = "lobotocraft:CoreMeltdownPreviousTeam";
    private static final String MELTDOWN_PREVIOUS_GLOW = "lobotocraft:CoreMeltdownPreviousGlow";
    private static final String MELTDOWN_TEAM = "loboto_meltdown";
    private static final UUID NETZACH_HEALTH_BONUS_UUID =
            UUID.fromString("5772de32-b59d-4dbf-a0b4-a2f5f4f485f5");
    private static final double NETZACH_HEALTH_BONUS = 5.0D;
    private static final int WORLD_SEARCH_LIMIT = 30_000_000;
    private static final Map<MinecraftServer, PendingChallenge> PENDING_CHALLENGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CoreSuppressionManager() {
    }

    public static String getStartDenialReason(ServerPlayer player, CoreSuppressionType type) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (state.isActive()) {
            return "当前已有核心抑制正在进行：" + state.getActiveType().getDisplayName();
        }
        if (!type.isImplemented()) {
            return type.getDisplayName() + " 核心抑制尚未实装。";
        }
        if (state.hasCompleted(type)) {
            return type.getDisplayName() + " 核心抑制已经完成。";
        }
        if (!state.hasCompletedPrerequisites(type)) {
            CoreSuppressionType previous = CoreSuppressionType.byOrdinal(type.ordinal() - 1);
            return "请先完成 " + (previous == null ? "前置" : previous.getDisplayName()) + " 核心抑制。";
        }

        int day = getPlayerDay(player);
        if (day < REQUIRED_DAY) {
            return "开启条件：玩家游玩天数达到26天。当前第 " + day + " 天。";
        }

        int observed = getFullyObservedCount(player);
        if (observed < REQUIRED_FULL_OBSERVATIONS) {
            return "开启条件：解锁26只异想体的全部信息。当前 " + observed + "/26。";
        }

        MinecraftServer server = player.getServer();
        ServerLevel companyLevel = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (companyLevel == null) {
            return "无法读取公司维度。";
        }
        OrdealData ordeal = OrdealData.get(server.overworld());
        if (ordeal.hasActiveOrdeal() || BlueMiddayEvent.isTrialActive()) {
            return "当前考验尚未结束，无法接取核心抑制。";
        }

        int abnormalityCount = 0;
        for (Entity entity : companyLevel.getAllEntities()) {
            if (!(entity instanceof AbstractAbnormality abnormality)
                    || !abnormality.isAlive() || abnormality.isRemoved()) {
                continue;
            }
            abnormalityCount++;
            if (abnormality.hasEscape()) {
                return "当前有异想体正在出逃，无法接取核心抑制。";
            }
        }
        if (abnormalityCount < REQUIRED_COMPANY_ABNORMALITIES) {
            return "开启条件：公司维度内至少存在26只异想体实体。当前 "
                    + abnormalityCount + "/26。";
        }
        return null;
    }

    public static boolean requestChallenge(ServerPlayer player, CoreSuppressionType type) {
        String denial = getStartDenialReason(player, type);
        if (denial != null) {
            player.sendSystemMessage(Component.literal("§c" + denial));
            return false;
        }

        MinecraftServer server = player.getServer();
        if (PENDING_CHALLENGES.containsKey(server)) {
            player.sendSystemMessage(Component.literal("§c已有核心抑制正在等待全员视觉安全确认。"));
            return false;
        }

        Set<UUID> participants = new HashSet<>();
        for (ServerPlayer participant : server.getPlayerList().getPlayers()) {
            participants.add(participant.getUUID());
        }
        PendingChallenge pending = new PendingChallenge(type, player.getUUID(),
                server.overworld().getGameTime() + VISUAL_WARNING_DURATION_TICKS,
                participants, false);
        PENDING_CHALLENGES.put(server, pending);
        sendPendingState(server, pending);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§e[核心抑制] 正在等待全员完成视觉安全确认，最多等待 60 秒。"), false);
        return true;
    }

    public static void submitVisualChoice(ServerPlayer player, boolean fullEffects) {
        MinecraftServer server = player.getServer();
        PendingChallenge pending = PENDING_CHALLENGES.get(server);
        if (pending == null || !pending.participants.contains(player.getUUID())
                || pending.visualChoices.containsKey(player.getUUID())) return;
        pending.visualChoices.put(player.getUUID(), fullEffects);
        if (pending.visualChoices.size() >= pending.participants.size()) {
            launchPendingChallenge(server, pending);
        } else {
            sendPendingState(server, pending);
        }
    }

    private static boolean beginChallenge(ServerPlayer player, CoreSuppressionType type,
                                          Map<UUID, Boolean> visualChoices) {
        String denial = getStartDenialReason(player, type);
        if (denial != null) {
            player.sendSystemMessage(Component.literal("§c" + denial));
            return false;
        }

        CompanyDailyData daily = getDailyData(player);
        if (daily == null) {
            player.sendSystemMessage(Component.literal("§c无法读取公司日程数据。"));
            return false;
        }

        if (!DayTransitionService.beginSuppressionDay(player)) {
            player.sendSystemMessage(Component.literal("§c新一天无法开始，核心抑制已取消。"));
            return false;
        }

        MinecraftServer server = player.getServer();
        KillCommand.queueActiveOrdealTargets(server);
        OrdealData ordeal = OrdealData.get(server.overworld());
        ordeal.resetForChallenge(daily.getCurrentDay());

        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        state.start(type, player.getUUID(), player.getGameProfile().getName(), daily.getCurrentDay(),
                ordeal.getDawnTriggerSerialToday(), ordeal.getMiddayTriggerSerialToday(), visualChoices);
        initializeMalkuthWorkState(server, state);
        reconcileHodEmployees(server, state);

        for (ServerPlayer participant : server.getPlayerList().getPlayers()) {
            if (participant.level().dimension() == ModDimensions.LOBOTO_KEY) {
                WorkDeviceItem.disableAll(participant);
            }
        }

        teleportToCompany(player);
        syncDaily(player, daily);
        broadcastTitle(server, type, "核心抑制", type.getDisplayName(), "挑战开始", type.getColor());
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§6[核心抑制] §f" + player.getGameProfile().getName() + " 开启了 "
                        + type.getDisplayName() + " 核心抑制。"), false);
        broadcastStopCommand(server);
        syncAll(server);
        return true;
    }

    /** Queues an implemented suppression without admission checks, but keeps the visual safety vote. */
    public static boolean debugStartChallenge(ServerPlayer player, CoreSuppressionType type) {
        if (type == null || !type.isImplemented()) return false;
        MinecraftServer server = player.getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (state.isActive() || PENDING_CHALLENGES.containsKey(server)) return false;

        Set<UUID> participants = new HashSet<>();
        for (ServerPlayer participant : server.getPlayerList().getPlayers()) {
            participants.add(participant.getUUID());
        }
        PendingChallenge pending = new PendingChallenge(type, player.getUUID(),
                server.overworld().getGameTime() + VISUAL_WARNING_DURATION_TICKS,
                participants, true);
        PENDING_CHALLENGES.put(server, pending);
        sendPendingState(server, pending);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§e[核心抑制] 强制挑战正在等待全员视觉安全确认，最多等待 60 秒。"), false);
        return true;
    }

    private static boolean beginDebugChallenge(ServerPlayer player, CoreSuppressionType type,
                                               Map<UUID, Boolean> visualChoices) {
        MinecraftServer server = player.getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (state.isActive()) return false;
        CompanyDailyData daily = getDailyData(player);
        if (daily == null) return false;
        KillCommand.queueActiveOrdealTargets(server);
        FacilityManager.debugResetSuppressionProgress(server.overworld());
        OrdealData ordeal = OrdealData.get(server.overworld());
        ordeal.resetForChallenge(daily.getCurrentDay());
        state.start(type, player.getUUID(), player.getGameProfile().getName(), daily.getCurrentDay(),
                ordeal.getDawnTriggerSerialToday(), ordeal.getMiddayTriggerSerialToday(), visualChoices);
        initializeMalkuthWorkState(server, state);
        reconcileHodEmployees(server, state);
        for (ServerPlayer participant : server.getPlayerList().getPlayers()) {
            if (participant.level().dimension() == ModDimensions.LOBOTO_KEY) WorkDeviceItem.disableAll(participant);
        }
        teleportToCompany(player);
        syncDaily(player, daily);
        broadcastTitle(server, type, "核心抑制", type.getDisplayName(), "调试挑战开始", type.getColor());
        broadcastStopCommand(server);
        syncAll(server);
        return true;
    }

    private static void broadcastStopCommand(MinecraftServer server) {
        Component message = Component.literal("[核心抑制] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("若视觉效果妨碍操作，可").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("点击这里").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                STOP_SUPPRESSION_COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("填入：" + STOP_SUPPRESSION_COMMAND)))))
                .append(Component.literal("以停止抑制（不视为完成）。").withStyle(ChatFormatting.WHITE));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static boolean isActive(Level level, CoreSuppressionType type) {
        return level instanceof ServerLevel serverLevel
                && CoreSuppressionData.get(serverLevel).getActiveType() == type;
    }

    public static boolean isSuppressionActive(Level level) {
        return level instanceof ServerLevel serverLevel
                && CoreSuppressionData.get(serverLevel).isActive();
    }

    public static boolean isDeviceRestricted(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && serverPlayer.level().dimension() == ModDimensions.LOBOTO_KEY
                && CoreSuppressionData.get(serverPlayer.serverLevel()).isActive();
    }

    public static boolean hasReward(Player player, CoreSuppressionType type) {
        if (!(player instanceof ServerPlayer serverPlayer) || getPlayerDay(serverPlayer) < REQUIRED_DAY) {
            return false;
        }
        return CoreSuppressionData.get(serverPlayer.serverLevel()).hasCompleted(type);
    }

    public static float getWorkSuccessBonus(ServerPlayer player) {
        return hasReward(player, CoreSuppressionType.MALKUTH)
                ? MALKUTH_COMPLETION_SUCCESS_BONUS : 0.0F;
    }

    public static int getAttributeGrowthMultiplier(ServerPlayer player) {
        return hasReward(player, CoreSuppressionType.HOD) ? 2 : 1;
    }

    public static float getWorkSpeedMultiplier(ServerPlayer player) {
        return hasReward(player, CoreSuppressionType.HOD) ? 1.10F : 1.0F;
    }

    public static int getIndependentPeBoxBonus(ServerPlayer player, int peOutput) {
        if (peOutput <= 0 || !hasReward(player, CoreSuppressionType.YESOD)) return 0;
        return Math.max(1, Math.round(peOutput * 0.25F));
    }

    public static boolean shouldDoubleWorkCount(ServerPlayer player) {
        return hasReward(player, CoreSuppressionType.YESOD) && player.getRandom().nextBoolean();
    }

    public static WorkType randomizeWorkType(ServerPlayer player, WorkType selected) {
        if (!isActive(player.level(), CoreSuppressionType.MALKUTH)) return selected;
        MinecraftServer server = player.getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        refreshMalkuthWorkState(server, state);
        return MalkuthWorkPolicy.map(selected, state.getMalkuthWorkMapping());
    }

    public static boolean isMalkuthWorkScrambled(Level level) {
        return isActive(level, CoreSuppressionType.MALKUTH);
    }

    public static boolean isMalkuthWorkCancellationLocked(Level level) {
        if (!isMalkuthWorkScrambled(level) || !(level instanceof ServerLevel serverLevel)) return false;
        return MalkuthWorkPolicy.isCancellationLockedForStoredLevel(
                FacilityDayData.get(serverLevel.getServer().overworld()).getMeltdownLevel());
    }

    public static boolean isNetzachReactorDisabled(Level level) {
        return isActive(level, CoreSuppressionType.NETZACH);
    }

    /** Restores employee vitals on every Qliphoth meltdown during Netzach suppression. */
    public static void onQliphothMeltdown(ServerLevel level) {
        if (!isActive(level, CoreSuppressionType.NETZACH)) return;
        forEachEmployee(level.getServer(), player -> {
            if (!player.isAlive()) return;
            player.setHealth(player.getMaxHealth());
            MentalValueUtil.setMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player));
            player.playNotifySound(ModSounds.NETZACH_CORE_HEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        });
    }

    public static boolean hasNetzachReward(Player player) {
        return hasReward(player, CoreSuppressionType.NETZACH);
    }

    public static void resolveMeltdownOnWorkStart(AbstractAbnormality abnormality, ServerPlayer player) {
        if (!isSuppressionActive(player.level()) || !isMeltdownActive(abnormality)) return;
        String name = abnormality.getAbnormalityName();
        clearMeltdownVisualAndData(abnormality);
        player.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "§a[融毁解除] §f" + name + " §a已开始工作，融毁倒计时解除。"), false);
    }

    public static void tickMeltdown(AbstractAbnormality abnormality) {
        // Migrate saves from the retired per-player meltdown implementation.
        // FacilityDayData is now the only authority for core-suppression meltdowns.
        clearMeltdownVisualAndData(abnormality);
    }

    /** Removes retired per-entity meltdown tags from pre-migration saves. */
    public static void retireLegacyMeltdown(AbstractAbnormality abnormality) {
        clearMeltdownVisualAndData(abnormality);
    }

    /** Server-authoritative state used by work validation and monitoring snapshots. */
    public static boolean isMeltdownActive(AbstractAbnormality abnormality) {
        return abnormality.getPersistentData().getBoolean(MELTDOWN_ACTIVE);
    }

    /** Remaining core-suppression meltdown time, or zero when inactive/expired. */
    public static int getMeltdownRemainingTicks(AbstractAbnormality abnormality) {
        if (!isMeltdownActive(abnormality) || !(abnormality.level() instanceof ServerLevel level)) return 0;
        long now = level.getServer().overworld().getGameTime();
        long remaining = abnormality.getPersistentData().getLong(MELTDOWN_DEADLINE) - now;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    private static void triggerRandomMeltdown(ServerPlayer player, AbstractAbnormality completedAbnormality) {
        if (!(player.level() instanceof ServerLevel level)) return;
        AABB wholeLevel = new AABB(-WORLD_SEARCH_LIMIT, level.getMinBuildHeight(), -WORLD_SEARCH_LIMIT,
                WORLD_SEARCH_LIMIT, level.getMaxBuildHeight(), WORLD_SEARCH_LIMIT);
        List<AbstractAbnormality> candidates = level.getEntitiesOfClass(AbstractAbnormality.class, wholeLevel,
                abnormality -> abnormality.isAlive()
                        && !abnormality.isRemoved()
                        && !abnormality.hasEscape()
                        && !abnormality.isToolType()
                        && abnormality.getQliphothCounter() > 0
                        && !isMeltdownActive(abnormality)
                        && EntityUtil.isInCompany(level, abnormality.blockPosition())
                        && (abnormality.getUUID().equals(completedAbnormality.getUUID())
                        || !WorkManager.isAbnormalityBeingWorked(abnormality)));
        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e[融毁] 当前没有可进入融毁的异想体。"));
            return;
        }

        AbstractAbnormality target = candidates.get(level.getRandom().nextInt(candidates.size()));
        if (target instanceof EntityDarkSkadi) {
            return;
        }
        startMeltdown(target, player);
    }

    private static void startMeltdown(AbstractAbnormality abnormality, ServerPlayer triggeringPlayer) {
        CompoundTag tag = abnormality.getPersistentData();
        tag.putBoolean(MELTDOWN_ACTIVE, true);
        tag.putLong(MELTDOWN_DEADLINE,
                triggeringPlayer.getServer().overworld().getGameTime() + MELTDOWN_DURATION_TICKS);
        tag.putUUID(MELTDOWN_PLAYER, triggeringPlayer.getUUID());
        tag.putBoolean(MELTDOWN_PREVIOUS_GLOW, abnormality.isCurrentlyGlowing());
        Team previousTeam = abnormality.getTeam();
        tag.putString(MELTDOWN_PREVIOUS_TEAM,
                previousTeam == null || MELTDOWN_TEAM.equals(previousTeam.getName()) ? "" : previousTeam.getName());
        applyMeltdownVisual(abnormality);

        MinecraftServer server = triggeringPlayer.getServer();
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§4[融毁警报] §c" + abnormality.getAbnormalityName()
                        + " 进入融毁！请在60秒内对其开始工作。"), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() == ModDimensions.LOBOTO_KEY) {
                player.playNotifySound(ModSounds.CORE_MELTDOWN_ALARM.get(), SoundSource.RECORDS, 1.0F, 1.0F);
            }
        }
    }

    private static void applyMeltdownVisual(AbstractAbnormality abnormality) {
        Scoreboard scoreboard = abnormality.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(MELTDOWN_TEAM);
        if (team == null) team = scoreboard.addPlayerTeam(MELTDOWN_TEAM);
        team.setColor(ChatFormatting.RED);
        if (abnormality.getTeam() != team) {
            scoreboard.addPlayerToTeam(abnormality.getScoreboardName(), team);
        }
        abnormality.setGlowingTag(true);
    }

    private static void clearMeltdownVisualAndData(AbstractAbnormality abnormality) {
        CompoundTag tag = abnormality.getPersistentData();
        if (!tag.getBoolean(MELTDOWN_ACTIVE)) return;

        Scoreboard scoreboard = abnormality.level().getScoreboard();
        PlayerTeam meltdownTeam = scoreboard.getPlayerTeam(MELTDOWN_TEAM);
        if (meltdownTeam != null && abnormality.getTeam() == meltdownTeam) {
            scoreboard.removePlayerFromTeam(abnormality.getScoreboardName(), meltdownTeam);
        }
        String previousTeamName = tag.getString(MELTDOWN_PREVIOUS_TEAM);
        if (!previousTeamName.isEmpty()) {
            PlayerTeam previousTeam = scoreboard.getPlayerTeam(previousTeamName);
            if (previousTeam != null) scoreboard.addPlayerToTeam(abnormality.getScoreboardName(), previousTeam);
        }
        if (!tag.getBoolean(MELTDOWN_PREVIOUS_GLOW)) {
            abnormality.setGlowingTag(false);
        }

        tag.remove(MELTDOWN_ACTIVE);
        tag.remove(MELTDOWN_DEADLINE);
        tag.remove(MELTDOWN_PLAYER);
        tag.remove(MELTDOWN_PREVIOUS_TEAM);
        tag.remove(MELTDOWN_PREVIOUS_GLOW);
    }

    private static int applyMeltdownPenalty(MinecraftServer server, CoreSuppressionData state,
                                            UUID playerUuid, int challengeDay) {
        if (playerUuid == null) return 0;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            state.queueMeltdownPenalty(playerUuid, challengeDay, MELTDOWN_WORK_PENALTY);
            return -1;
        }
        return deductDailyWork(player, MELTDOWN_WORK_PENALTY);
    }

    private static int deductDailyWork(ServerPlayer player, int amount) {
        CompanyDailyData daily = getDailyData(player);
        if (daily == null || amount <= 0) return 0;
        int before = daily.getTodayWorkCount();
        daily.setTodayWorkCount(before - amount);
        syncDaily(player, daily);
        return before - daily.getTodayWorkCount();
    }

    private static void applyPendingMeltdownPenalty(ServerPlayer player) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        int[] pending = state.takePendingMeltdownPenalty(player.getUUID());
        // Pending entries were written by the retired per-player meltdown
        // implementation. Reading removes them; never apply its obsolete
        // work-count penalty after upgrading a save.
        if (pending != null) return;
        if (pending == null || pending.length < 2) return;
        CompanyDailyData daily = getDailyData(player);
        if (daily == null || daily.getCurrentDay() != pending[0]) return;
        int deducted = deductDailyWork(player, pending[1]);
        player.sendSystemMessage(Component.literal(
                "§c[融毁惩罚] 离线期间积累的当日工作次数惩罚已结算：-" + deducted + "。"));
    }

    public static boolean requirementsMet(MinecraftServer server, CoreSuppressionData state) {
        CoreSuppressionType type = state.getActiveType();
        if (!state.isActive() || type == null || !type.isImplemented()
                || type.getRequiredBossPhases() > 0) {
            return false;
        }
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        return type.isFacilityObjectiveMet(
                facility.getEnergy(), facility.getEnergyQuota(), facility.getMeltdownLevel());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        processPendingChallenge(server);
        if (server.getTickCount() % 20 == 0) {
            processChallenge(server);
            syncAll(server);
        }
        if (server.getTickCount() % 120 == 0) {
            spawnFloatingQuotes(server);
        }
        if (server.getTickCount() % 100 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                applyPendingHodRestore(player);
                applyNetzachHealthBonus(player);
            }
        }
    }

    private static void spawnFloatingQuotes(MinecraftServer server) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (!state.isActive()) return;
        CoreSuppressionType type = state.getActiveType();
        if (type == null) return;

        // 只在接取者所在维度、给在场玩家周围生成
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() != ModDimensions.LOBOTO_KEY) continue;
            if (player.level() instanceof ServerLevel level) {
                CoreSuppressionQuotes.spawnRandomQuote(level, player, type);
            }
        }
    }

    private static void processChallenge(MinecraftServer server) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (!state.isActive()) return;

        reconcileHodEmployees(server, state);

        OrdealData ordeal = OrdealData.get(server.overworld());
        int dawnSerial = ordeal.getDawnTriggerSerialToday();
        int middaySerial = ordeal.getMiddayTriggerSerialToday();
        CoreSuppressionType type = state.getActiveType();
        if (type == CoreSuppressionType.MALKUTH) {
            refreshMalkuthWorkState(server, state);
        }

        for (int i = state.getSeenDawnTriggerSerial(); i < dawnSerial; i++) {
            handleOrdealTrigger(server, state, type, false, i == 0);
        }
        for (int i = state.getSeenMiddayTriggerSerial(); i < middaySerial; i++) {
            handleOrdealTrigger(server, state, type, true, i == 0);
        }
        state.setSeenTriggerSerials(dawnSerial, middaySerial);
        state.setProgress(ordeal.getDawnCompletionsToday(), ordeal.getMiddayCompletionsToday());
    }

    private static void initializeMalkuthWorkState(MinecraftServer server, CoreSuppressionData state) {
        if (state.getActiveType() != CoreSuppressionType.MALKUTH) return;
        int meltdownLevel = FacilityDayData.get(server.overworld()).getMeltdownLevel();
        state.setMalkuthWorkState(
                MalkuthWorkPolicy.generateMapping(server.overworld().getRandom()::nextInt),
                meltdownLevel);
    }

    private static void refreshMalkuthWorkState(MinecraftServer server, CoreSuppressionData state) {
        if (!state.isActive() || state.getActiveType() != CoreSuppressionType.MALKUTH) return;
        int meltdownLevel = FacilityDayData.get(server.overworld()).getMeltdownLevel();
        if (state.hasMalkuthWorkMapping()
                && state.getMalkuthObservedMeltdownLevel() == meltdownLevel) return;
        state.setMalkuthWorkState(
                MalkuthWorkPolicy.generateMapping(server.overworld().getRandom()::nextInt),
                meltdownLevel);
    }

    private static void handleOrdealTrigger(MinecraftServer server, CoreSuppressionData state,
                                            CoreSuppressionType type, boolean midday, boolean firstOfTier) {
        if (type == CoreSuppressionType.YESOD && firstOfTier) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.playNotifySound(ModSounds.YESOD_CORE_STAGE.get(), SoundSource.PLAYERS,
                        1.0F, midday ? 0.8F : 1.0F);
            }
        }
    }

    private static void reconcileHodEmployees(MinecraftServer server, CoreSuppressionData state) {
        int penalty = state.isActive() && state.getActiveType() == CoreSuppressionType.HOD
                ? HodPenaltyPolicy.penalty(FacilityDayData.get(server.overworld()).getMeltdownLevel())
                : 0;
        forEachEmployee(server, player -> applyHodPenalty(player, penalty));
    }

    /** Reconciles one real or simulated employee after login, respawn, or AI creation. */
    public static void reconcileHodState(ServerPlayer player) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        int penalty = state.isActive() && state.getActiveType() == CoreSuppressionType.HOD
                ? HodPenaltyPolicy.penalty(FacilityDayData.get(player.serverLevel()).getMeltdownLevel())
                : 0;
        applyHodPenalty(player, penalty);
    }

    private static void applyHodPenalty(ServerPlayer player, int penalty) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            if (!(stats instanceof EmployeeStats values) || values.getHodPenalty() == penalty) return;
            values.setHodPenalty(penalty);
            EmployeeStatsApplier.applyAllAttributes(player);
            if (!(player instanceof EmployeeServerPlayer)) syncStats(player);
        });
    }

    private static void forEachEmployee(MinecraftServer server, Consumer<ServerPlayer> action) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) action.accept(player);
        for (EmployeeServerPlayer employee : EmployeeRuntimeManager.activePlayers()) action.accept(employee);
    }

    private static void applyPendingHodRestore(ServerPlayer player) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        int[] restore = state.takePendingHodRestore(player.getUUID());
        if (restore == null) {
            reconcileHodState(player);
            return;
        }
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            if (stats instanceof EmployeeStats values) {
                // Legacy saves stored Hod's reduction as a mutation of the base
                // values. Restore those values from the unmodified base once;
                // the current overlay path never populates this map.
                values.clearHodPenalty();
                values.setFortitude(values.getBaseFortitude() + restore[0]);
                values.setPrudence(values.getBasePrudence() + restore[1]);
                values.setTemperance(values.getBaseTemperance() + restore[2]);
                values.setJustice(values.getBaseJustice() + restore[3]);
            } else {
                stats.setFortitude(stats.getFortitude() + restore[0]);
                stats.setPrudence(stats.getPrudence() + restore[1]);
                stats.setTemperance(stats.getTemperance() + restore[2]);
                stats.setJustice(stats.getJustice() + restore[3]);
            }
            EmployeeStatsApplier.applyAllAttributes(player);
            if (!(player instanceof EmployeeServerPlayer)) {
                syncStats(player);
                player.sendSystemMessage(Component.literal("§aHod核心抑制造成的临时属性降低已恢复。"));
            }
        });
        reconcileHodState(player);
    }

    private static void applyNetzachHealthBonus(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        MobEffectInstance legacyEffect = player.getEffect(MobEffects.HEALTH_BOOST);
        if (legacyEffect != null && legacyEffect.getAmplifier() == 1
                && legacyEffect.getDuration() == -1 && !legacyEffect.isVisible()) {
            player.removeEffect(MobEffects.HEALTH_BOOST);
        }

        AttributeModifier current = maxHealth.getModifier(NETZACH_HEALTH_BONUS_UUID);
        if (!hasCompletedAdvancement(player, CoreSuppressionType.NETZACH)
                || getPlayerDay(player) < REQUIRED_DAY) {
            if (current != null) maxHealth.removeModifier(NETZACH_HEALTH_BONUS_UUID);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
            return;
        }
        if (current == null || current.getAmount() != NETZACH_HEALTH_BONUS
                || current.getOperation() != AttributeModifier.Operation.ADDITION) {
            if (current != null) maxHealth.removeModifier(NETZACH_HEALTH_BONUS_UUID);
            maxHealth.addPermanentModifier(new AttributeModifier(
                    NETZACH_HEALTH_BONUS_UUID,
                    "Netzach Core Suppression Health Bonus",
                    NETZACH_HEALTH_BONUS,
                    AttributeModifier.Operation.ADDITION
            ));
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    @SubscribeEvent
    public static void onWorkDamage(WorkDamageEvent event) {
        if (hasReward(event.getPlayer(), CoreSuppressionType.MALKUTH)
                && event.getPlayer().getRandom().nextBoolean()) {
            event.setCancelMessage("§aMalkuth核心抑制奖励免除了本次工作失误伤害。§r");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDayAdvance(CompanyDayAdvanceEvent event) {
        MinecraftServer server = event.getPlayer().getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (!state.isActive()) return;

        if (!requirementsMet(server, state)) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal(
                    "§c核心抑制结束前无法进入下一天。需要完成当天能源配额并到达逆卡巴拉熔毁等级 VI。"));
            return;
        }
        finishChallenge(server, true, "考验完成");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (!state.isActive()) return;

        event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        player.sendSystemMessage(Component.literal(
                "§c核心抑制期间不能通过睡觉结束当天，请使用设施的结束日操作。"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (!state.isActive()) return;
        event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        PendingChallenge pending = PENDING_CHALLENGES.get(server);
        if (pending != null) {
            if (player.getUUID().equals(pending.ownerUuid)) {
                cancelPendingChallenge(server, pending, "发起者退出了游戏，核心抑制确认已取消。");
            } else if (pending.participants.remove(player.getUUID())) {
                pending.visualChoices.remove(player.getUUID());
                if (pending.visualChoices.size() >= pending.participants.size()) {
                    launchPendingChallenge(server, pending);
                } else {
                    sendPendingState(server, pending);
                }
            }
        }
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (state.isActive() && player.getUUID().equals(state.getOwnerUuid())) {
            finishChallenge(server, false, "接取者退出了游戏");
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingChallenge pending = PENDING_CHALLENGES.get(player.getServer());
        if (pending != null && player.getServer().overworld().getGameTime() < pending.deadline) {
            pending.participants.add(player.getUUID());
            sendPendingState(player.getServer(), pending);
        }
        applyPendingHodRestore(player);
        applyPendingMeltdownPenalty(player);
        grantCompletedAdvancements(player);
        applyNetzachHealthBonus(player);
        syncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyNetzachHealthBonus(player);
            reconcileHodState(player);
        }
    }

    public static void failChallenge(MinecraftServer server, String reason) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (state.isActive()) finishChallenge(server, false, reason);
    }

    /** Finishes the active suppression without checking its completion conditions. */
    public static boolean debugFinishChallenge(MinecraftServer server, boolean success) {
        if (!CoreSuppressionData.get(server.overworld()).isActive()) return false;
        finishChallenge(server, success, "调试命令");
        return true;
    }

    private static void finishChallenge(MinecraftServer server, boolean success, String reason) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        CoreSuppressionType type = state.getActiveType();
        if (type == null) return;

        if (type == CoreSuppressionType.HOD) state.prepareHodRestores();
        state.finish(success);
        forEachEmployee(server, CoreSuppressionManager::applyPendingHodRestore);

        if (success) {
            KillCommand.queueAllSuppressionTargets(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                grantAdvancement(player, type);
                applyNetzachHealthBonus(player);
            }
            broadcastTitle(server, type, "核心抑制完成", type.getDisplayName(), reason, type.getColor());
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§a[核心抑制] §f" + type.getDisplayName() + " 核心抑制已完成，永久加成已解锁。"), false);
        } else {
            broadcastTitle(server, type, "核心抑制失败", type.getDisplayName(), reason, 0xC43A3A);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§c[核心抑制] " + type.getDisplayName() + " 挑战失败：" + reason), false);
        }
        syncAll(server);
        // Alert packets are ignored while suppression is active. Send the
        // authoritative level only after clients receive the inactive state.
        EscapeTracker.getInstance().syncAll(server);
    }

    public static void grantCompletedAdvancements(ServerPlayer player) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        for (CoreSuppressionType type : CoreSuppressionType.values()) {
            if (state.hasCompleted(type)) grantAdvancement(player, type);
        }
    }

    private static void grantAdvancement(ServerPlayer player, CoreSuppressionType type) {
        var advancement = player.getServer().getAdvancements().getAdvancement(type.getAdvancementId());
        if (advancement != null) player.getAdvancements().award(advancement, "completed");
    }

    private static boolean hasCompletedAdvancement(ServerPlayer player, CoreSuppressionType type) {
        var advancement = player.getServer().getAdvancements().getAdvancement(type.getAdvancementId());
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncTo(player);
    }

    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        boolean active = state.isActive();
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        int energy = active ? facility.getEnergy() : 0;
        int energyQuota = active ? facility.getEnergyQuota() : 0;
        int meltdownLevel = active ? facility.getMeltdownLevel() : 0;
        int stage = 0;
        if (active) {
            // The original upper-layer suppressions change phase at Qliphoth
            // overload levels I and III. FacilityDayData stores the next level,
            // so completed activations I and III are represented by II and IV.
            stage = HodPenaltyPolicy.visualStage(meltdownLevel);
        }
        CoreSuppressionType type = state.getActiveType();
        MessageLoader.getLoader().sendToPlayer(player, new CoreSuppressionSyncPacket(
                active,
                type == null ? -1 : type.ordinal(),
                state.getOwnerName(),
                energy,
                energyQuota,
                meltdownLevel,
                type == null ? 0 : type.getRequiredMeltdownLevel(),
                stage,
                state.getCompletedMask(),
                state.usesFullVisualEffects(player.getUUID())
        ));
    }

    private static void processPendingChallenge(MinecraftServer server) {
        PendingChallenge pending = PENDING_CHALLENGES.get(server);
        if (pending == null) return;
        if (server.getPlayerList().getPlayer(pending.ownerUuid) == null) {
            cancelPendingChallenge(server, pending, "发起者已离线，核心抑制确认已取消。");
            return;
        }
        long remaining = pending.deadline - server.overworld().getGameTime();
        if (remaining <= 0L || pending.visualChoices.size() >= pending.participants.size()) {
            launchPendingChallenge(server, pending);
            return;
        }
        int seconds = remainingSeconds(remaining);
        if (seconds != pending.lastBroadcastSeconds) {
            pending.lastBroadcastSeconds = seconds;
            sendPendingState(server, pending);
        }
    }

    private static void launchPendingChallenge(MinecraftServer server, PendingChallenge pending) {
        if (PENDING_CHALLENGES.get(server) != pending) return;
        PENDING_CHALLENGES.remove(server);
        for (UUID participant : pending.participants) {
            pending.visualChoices.putIfAbsent(participant, false);
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerUuid);
        boolean started = owner != null && (pending.debug
                ? beginDebugChallenge(owner, pending.type, pending.visualChoices)
                : beginChallenge(owner, pending.type, pending.visualChoices));
        if (!started) {
            clearPendingScreens(server, pending);
        }
    }

    private static void cancelPendingChallenge(MinecraftServer server, PendingChallenge pending,
                                               String reason) {
        if (PENDING_CHALLENGES.get(server) != pending) return;
        PENDING_CHALLENGES.remove(server);
        clearPendingScreens(server, pending);
        server.getPlayerList().broadcastSystemMessage(Component.literal("§c[核心抑制] " + reason), false);
    }

    private static void clearPendingScreens(MinecraftServer server, PendingChallenge pending) {
        for (UUID participantId : pending.participants) {
            ServerPlayer participant = server.getPlayerList().getPlayer(participantId);
            if (participant != null) {
                MessageLoader.getLoader().sendToPlayer(participant,
                        new CoreSuppressionWarningPacket(-1, 0, 0, 0, false));
            }
        }
    }

    private static void sendPendingState(MinecraftServer server, PendingChallenge pending) {
        long remaining = Math.max(0L, pending.deadline - server.overworld().getGameTime());
        int seconds = remainingSeconds(remaining);
        int confirmed = pending.visualChoices.size();
        int total = pending.participants.size();
        for (UUID participantId : pending.participants) {
            ServerPlayer participant = server.getPlayerList().getPlayer(participantId);
            if (participant == null) continue;
            boolean needsChoice = !pending.visualChoices.containsKey(participantId);
            MessageLoader.getLoader().sendToPlayer(participant, new CoreSuppressionWarningPacket(
                    pending.type.ordinal(), seconds, confirmed, total, needsChoice));
        }
    }

    private static int remainingSeconds(long ticks) {
        return (int) Math.max(0L, Math.min(60L, (ticks + 19L) / 20L));
    }

    private static final class PendingChallenge {
        private final CoreSuppressionType type;
        private final UUID ownerUuid;
        private final long deadline;
        private final Set<UUID> participants;
        private final boolean debug;
        private final Map<UUID, Boolean> visualChoices = new HashMap<>();
        private int lastBroadcastSeconds = -1;

        private PendingChallenge(CoreSuppressionType type, UUID ownerUuid,
                                 long deadline, Set<UUID> participants, boolean debug) {
            this.type = type;
            this.ownerUuid = ownerUuid;
            this.deadline = deadline;
            this.participants = new HashSet<>(participants);
            this.debug = debug;
        }
    }

    private static CompanyDailyData getDailyData(ServerPlayer player) {
        return player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).resolve().orElse(null);
    }

    private static int getPlayerDay(ServerPlayer player) {
        CompanyDailyData data = getDailyData(player);
        return data == null ? 1 : data.getCurrentDay();
    }

    private static int getFullyObservedCount(ServerPlayer player) {
        return player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .map(data -> data.countFullyObservedAbnormalities())
                .orElse(0);
    }

    private static void syncDaily(ServerPlayer player, CompanyDailyData daily) {
        MessageLoader.getLoader().sendToPlayer(player, new CompanyDailySyncPacket(
                daily.getCurrentDay(), daily.getTodayWorkCount(), daily.isArmorLocked(), daily.isHasSleep()));
    }

    private static void syncStats(ServerPlayer player) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats ->
                MessageLoader.getLoader().sendToPlayer(player, new EmployeeStatsSyncPacket(
                        stats.getFortitude(), stats.getPrudence(), stats.getTemperance(), stats.getJustice())));
    }

    private static void broadcastTitle(MinecraftServer server, CoreSuppressionType type,
                                       String top, String title, String subtitle, int color) {
        OrdealTitlePacket packet = new OrdealTitlePacket(top, title, subtitle, color, 10, 80, 20);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(player, packet);
        }
    }

    private static void teleportToCompany(ServerPlayer player) {
        if (player.level().dimension() == ModDimensions.LOBOTO_KEY) return;
        CompoundTag nbt = player.getPersistentData();
        nbt.putDouble(RETURN_X, player.getX());
        nbt.putDouble(RETURN_Y, player.getY());
        nbt.putDouble(RETURN_Z, player.getZ());
        nbt.putString(RETURN_LEVEL, player.level().dimension().location().toString());

        double x = nbt.contains(RETURN_X + "_loboto") ? nbt.getDouble(RETURN_X + "_loboto") : 0.5D;
        double y = nbt.contains(RETURN_Y + "_loboto") ? nbt.getDouble(RETURN_Y + "_loboto") : 4.0D;
        double z = nbt.contains(RETURN_Z + "_loboto") ? nbt.getDouble(RETURN_Z + "_loboto") : 0.5D;
        if (Structures.LOBOTO.isGenerated(player.serverLevel()) && x == 0.5D && y == 4.0D) {
            x = 195.0D;
            y = 273.0D;
            z = 29.0D;
        }
        EntityUtil.teleportPlayer(player, ModDimensions.LOBOTO_KEY, BlockPos.containing(x, y, z));
    }

    public static void retainHodDeathStats(Player original, EmployeeStats stats) {
        if (!hasReward(original, CoreSuppressionType.HOD)) return;
        stats.setFortitude(60);
        stats.setPrudence(60);
        stats.setTemperance(60);
        stats.setJustice(60);
    }
}
