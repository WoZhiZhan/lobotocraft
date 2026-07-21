package com.wzz.lobotocraft.core_suppression;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyData;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.command.KillCommand;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.event.definition.work.WorkDamageEvent;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.WorkDeviceItem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionSyncPacket;
import com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket;
import com.wzz.lobotocraft.network.packet.OrdealTitlePacket;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import com.wzz.lobotocraft.world.structure.Structures;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class CoreSuppressionManager {
    public static final int REQUIRED_DAY = 26;
    public static final int REQUIRED_FULL_OBSERVATIONS = 26;
    public static final int REQUIRED_DAWNS = 3;
    public static final int REQUIRED_MIDDAYS = 2;

    private static final String RETURN_X = "lobotocraft:LotoOldX";
    private static final String RETURN_Y = "lobotocraft:LotoOldY";
    private static final String RETURN_Z = "lobotocraft:LotoOldZ";
    private static final String RETURN_LEVEL = "lobotocraft:LotoOldLevelName";

    private CoreSuppressionManager() {
    }

    public static String getStartDenialReason(ServerPlayer player, CoreSuppressionType type) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (state.isActive()) {
            return "当前已有核心抑制正在进行：" + state.getActiveType().getDisplayName();
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
        return null;
    }

    public static boolean startChallenge(ServerPlayer player, CoreSuppressionType type) {
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

        daily.setOwner(player);
        int oldDay = daily.getCurrentDay();
        daily.advanceToNextDay();
        if (daily.getCurrentDay() == oldDay) {
            player.sendSystemMessage(Component.literal("§c新一天无法开始，核心抑制已取消。"));
            return false;
        }
        daily.setTodayWorkCount(0);
        daily.setHasSleep(false);

        MinecraftServer server = player.getServer();
        KillCommand.queueActiveOrdealTargets(server);
        OrdealData ordeal = OrdealData.get(server.overworld());
        ordeal.resetForChallenge(daily.getCurrentDay());

        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        state.start(type, player.getUUID(), player.getGameProfile().getName(), daily.getCurrentDay(),
                ordeal.getDawnTriggerSerialToday(), ordeal.getMiddayTriggerSerialToday());

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
        syncAll(server);
        return true;
    }

    public static boolean isActive(Level level, CoreSuppressionType type) {
        return level instanceof ServerLevel serverLevel
                && CoreSuppressionData.get(serverLevel).getActiveType() == type;
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
        return hasReward(player, CoreSuppressionType.MALKUTH) ? 0.10F : 0.0F;
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
        WorkType[] types = WorkType.values();
        return types[player.getRandom().nextInt(types.length)];
    }

    public static boolean isNetzachReactorDisabled(Level level) {
        return isActive(level, CoreSuppressionType.NETZACH);
    }

    public static boolean hasNetzachReward(Player player) {
        return hasReward(player, CoreSuppressionType.NETZACH);
    }

    public static boolean requirementsMet(MinecraftServer server, CoreSuppressionData state) {
        if (!state.isActive() || state.getDawnCompleted() < REQUIRED_DAWNS
                || state.getMiddayCompleted() < REQUIRED_MIDDAYS) {
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(state.getOwnerUuid());
        CompanyDailyData daily = owner == null ? null : getDailyData(owner);
        return daily != null && daily.hasCompletedTodayWork();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
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
                applyNetzachHealthBoost(player);
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

        OrdealData ordeal = OrdealData.get(server.overworld());
        int dawnSerial = ordeal.getDawnTriggerSerialToday();
        int middaySerial = ordeal.getMiddayTriggerSerialToday();
        CoreSuppressionType type = state.getActiveType();

        for (int i = state.getSeenDawnTriggerSerial(); i < dawnSerial; i++) {
            handleOrdealTrigger(server, state, type, false, i == 0);
        }
        for (int i = state.getSeenMiddayTriggerSerial(); i < middaySerial; i++) {
            handleOrdealTrigger(server, state, type, true, i == 0);
        }
        state.setSeenTriggerSerials(dawnSerial, middaySerial);
        state.setProgress(ordeal.getDawnCompletionsToday(), ordeal.getMiddayCompletionsToday());
    }

    private static void handleOrdealTrigger(MinecraftServer server, CoreSuppressionData state,
                                            CoreSuppressionType type, boolean midday, boolean firstOfTier) {
        if (type == CoreSuppressionType.HOD) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                applyRandomHodPenalty(player, state);
            }
        } else if (type == CoreSuppressionType.NETZACH) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive()) continue;
                player.setHealth(player.getMaxHealth());
                MentalValueUtil.setMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player));
                player.playNotifySound(ModSounds.NETZACH_CORE_HEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        } else if (type == CoreSuppressionType.YESOD && firstOfTier) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.playNotifySound(ModSounds.YESOD_CORE_STAGE.get(), SoundSource.PLAYERS,
                        1.0F, midday ? 0.8F : 1.0F);
            }
        }
    }

    private static void applyRandomHodPenalty(ServerPlayer player, CoreSuppressionData state) {
        int statIndex = player.getRandom().nextInt(4);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int before;
            switch (statIndex) {
                case 0 -> {
                    before = stats.getFortitude();
                    stats.setFortitude(before - 20);
                }
                case 1 -> {
                    before = stats.getPrudence();
                    stats.setPrudence(before - 20);
                }
                case 2 -> {
                    before = stats.getTemperance();
                    stats.setTemperance(before - 20);
                }
                default -> {
                    before = stats.getJustice();
                    stats.setJustice(before - 20);
                }
            }
            int after = switch (statIndex) {
                case 0 -> stats.getFortitude();
                case 1 -> stats.getPrudence();
                case 2 -> stats.getTemperance();
                default -> stats.getJustice();
            };
            state.addHodPenalty(player.getUUID(), statIndex, before - after);
            EmployeeStatsApplier.applyAllAttributes(player);
            syncStats(player);
            player.sendSystemMessage(Component.literal("§cHod核心抑制：随机属性等级暂时下降。"));
        });
    }

    private static void applyPendingHodRestore(ServerPlayer player) {
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        int[] restore = state.takePendingHodRestore(player.getUUID());
        if (restore == null) return;
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(stats.getFortitude() + restore[0]);
            stats.setPrudence(stats.getPrudence() + restore[1]);
            stats.setTemperance(stats.getTemperance() + restore[2]);
            stats.setJustice(stats.getJustice() + restore[3]);
            EmployeeStatsApplier.applyAllAttributes(player);
            syncStats(player);
            player.sendSystemMessage(Component.literal("§aHod核心抑制造成的临时属性降低已恢复。"));
        });
    }

    private static void applyNetzachHealthBoost(ServerPlayer player) {
        if (!hasReward(player, CoreSuppressionType.NETZACH)) return;
        MobEffectInstance effect = player.getEffect(MobEffects.HEALTH_BOOST);
        if (effect == null || effect.getAmplifier() < 1 || effect.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, -1, 1, false, false, true));
        }
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

        if (!event.getPlayer().getUUID().equals(state.getOwnerUuid()) || !requirementsMet(server, state)) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal(
                    "§c核心抑制结束前无法进入下一天。需要完成三次黎明、两次正午和当日工作。"));
            return;
        }
        finishChallenge(server, true, "考验完成");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (!state.isActive()) return;

        boolean ownerReady = player.getUUID().equals(state.getOwnerUuid())
                && player.level().dimension() == ModDimensions.LOBOTO_KEY
                && requirementsMet(player.getServer(), state);
        if (!ownerReady) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.sendSystemMessage(Component.literal(
                    "§c核心抑制结束前无法休息。仅接取者在完成全部目标后可以结束这一天。"));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (!state.isActive()) return;
        boolean ownerReady = player.getUUID().equals(state.getOwnerUuid())
                && player.level().dimension() == ModDimensions.LOBOTO_KEY
                && requirementsMet(player.getServer(), state);
        event.setResult(ownerReady ? Event.Result.ALLOW : Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CoreSuppressionData state = CoreSuppressionData.get(player.serverLevel());
        if (state.isActive() && player.getUUID().equals(state.getOwnerUuid())) {
            finishChallenge(player.getServer(), false, "接取者退出了游戏");
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyPendingHodRestore(player);
        grantCompletedAdvancements(player);
        applyNetzachHealthBoost(player);
        syncTo(player);
    }

    public static void failChallenge(MinecraftServer server, String reason) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        if (state.isActive()) finishChallenge(server, false, reason);
    }

    private static void finishChallenge(MinecraftServer server, boolean success, String reason) {
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        CoreSuppressionType type = state.getActiveType();
        if (type == null) return;

        if (type == CoreSuppressionType.HOD) state.prepareHodRestores();
        state.finish(success);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyPendingHodRestore(player);
        }

        if (success) {
            KillCommand.queueAllSuppressionTargets(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                grantAdvancement(player, type);
                applyNetzachHealthBoost(player);
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

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncTo(player);
    }

    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CoreSuppressionData state = CoreSuppressionData.get(server.overworld());
        boolean active = state.isActive();
        int work = 0;
        int required = 0;
        int stage = 0;
        if (active) {
            ServerPlayer owner = server.getPlayerList().getPlayer(state.getOwnerUuid());
            CompanyDailyData daily = owner == null ? null : getDailyData(owner);
            if (daily != null) {
                work = daily.getTodayWorkCount();
                required = daily.getRequiredWorkCount();
            }
            OrdealData ordeal = OrdealData.get(server.overworld());
            stage = ordeal.getMiddayTriggerSerialToday() > 0 ? 3
                    : ordeal.getDawnTriggerSerialToday() > 0 ? 2 : 1;
        }
        CoreSuppressionType type = state.getActiveType();
        MessageLoader.getLoader().sendToPlayer(player, new CoreSuppressionSyncPacket(
                active,
                type == null ? -1 : type.ordinal(),
                state.getOwnerName(),
                state.getDawnCompleted(),
                state.getMiddayCompleted(),
                work,
                required,
                stage,
                state.getCompletedMask()
        ));
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
