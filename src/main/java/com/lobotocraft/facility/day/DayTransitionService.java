package com.lobotocraft.facility.day;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.FacilityPatch;
import com.lobotocraft.facility.api.DayCompletionRequirements;
import com.lobotocraft.facility.config.FacilityServerConfig;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.util.ItemUtil;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

public final class DayTransitionService {
    private static final Map<MinecraftServer, DayVoteState> ACTIVE = new IdentityHashMap<>();

    private DayTransitionService() {
    }

    public static void requestStart(ServerPlayer player) {
        if (!isEligibleRealPlayer(player)) {
            reject(player, "只能由公司内的真实主管结束当天。", "ineligible player");
            return;
        }
        FacilityDayData data = FacilityDayData.get(player.serverLevel());
        if (!DayCompletionRequirements.allSatisfied(player, data)) {
            DayCompletionRequirements.unmetMessages(player, data).forEach(message ->
                    player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false));
            return;
        }
        MinecraftServer server = player.server;
        DayVoteState current = ACTIVE.get(server);
        if (current != null && current.day() == data.getDay()) {
            player.displayClientMessage(Component.literal("结束日投票已经进行中。")
                    .withStyle(ChatFormatting.YELLOW), false);
            FacilityManager.syncAll(server, data);
            return;
        }

        Set<UUID> eligible = new LinkedHashSet<>();
        for (ServerPlayer candidate : realCompanyPlayers(server)) eligible.add(candidate.getUUID());
        if (eligible.size() <= 1) {
            commit(server, player, data.getDay());
            return;
        }

        long deadline = server.overworld().getGameTime() + FacilityServerConfig.DAY_VOTE_DURATION_TICKS.get();
        DayVoteState vote = new DayVoteState(data.getDay(), deadline,
                FacilityServerConfig.DAY_VOTE_APPROVAL_RATIO.get(), eligible);
        vote.cast(player.getUUID(), DayVoteState.Vote.APPROVE);
        ACTIVE.put(server, vote);
        broadcast(server, Component.literal("结束今日工作的投票已开始").withStyle(ChatFormatting.AQUA));
        FacilityManager.syncAll(server, data);
    }

    public static void cast(ServerPlayer player, DayVoteState.Vote choice) {
        DayVoteState vote = ACTIVE.get(player.server);
        if (vote == null || !isEligibleRealPlayer(player) || !vote.cast(player.getUUID(), choice)) return;
        FacilityDayData data = FacilityDayData.get(player.serverLevel());
        resolve(player.server, vote, data, player.server.overworld().getGameTime());
        FacilityManager.syncAll(player.server, data);
    }

    public static void tick(MinecraftServer server, FacilityDayData data, long gameTime) {
        DayVoteState vote = ACTIVE.get(server);
        if (vote == null) return;
        if (vote.day() != data.getDay() || data.getEnergy() < data.getEnergyQuota()) {
            cancel(server, "结束日条件已变化，投票已取消");
            FacilityManager.syncAll(server, data);
            return;
        }
        resolve(server, vote, data, gameTime);
    }

    private static void resolve(MinecraftServer server, DayVoteState vote,
                                FacilityDayData data, long gameTime) {
        DayVoteState.Outcome outcome = vote.outcome(gameTime);
        if (outcome == DayVoteState.Outcome.PENDING) return;
        ACTIVE.remove(server);
        if (outcome == DayVoteState.Outcome.FAILED) {
            broadcast(server, Component.literal("结束今日工作的投票未通过").withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayer actor = realCompanyPlayers(server).stream()
                .filter(player -> vote.eligible().contains(player.getUUID()))
                .findFirst().orElse(null);
        if (actor == null) {
            broadcast(server, Component.literal("公司内没有可执行结束日的主管，投票已取消")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        commit(server, actor, vote.day());
    }

    private static boolean commit(MinecraftServer server, ServerPlayer actor, int expectedDay) {
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        if (facility.getDay() != expectedDay) {
            reject(actor, "结束当天失败：设施天数已经变化，请松开按键后重试。",
                    "expected DAY " + expectedDay + " but facility is DAY " + facility.getDay());
            return false;
        }
        if (!DayCompletionRequirements.allSatisfied(actor, facility)) {
            DayCompletionRequirements.unmetMessages(actor, facility).forEach(message ->
                    actor.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false));
            FacilityPatch.LOGGER.warn("Rejected end-day commit for {} on DAY {}: requirements changed",
                    actor.getGameProfile().getName(), expectedDay);
            return false;
        }
        ACTIVE.remove(server);
        if (!advanceActorDay(actor, expectedDay)) {
            reject(actor, "结束当天失败：天数推进被其他流程取消，请检查聊天提示后重试。",
                    "player capability missing or CompanyDayAdvanceEvent cancelled");
            return false;
        }
        if (!completeFacilityDay(server, actor, expectedDay)) {
            actor.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                    .ifPresent(daily -> daily.setCurrentDay(expectedDay));
            reject(actor, "结束当天失败：设施状态未能推进，玩家天数已回滚，请重试。",
                    "facility remained on DAY " + facility.getDay());
            return false;
        }
        FacilityPatch.LOGGER.info("{} completed facility DAY {} and advanced to DAY {}",
                actor.getGameProfile().getName(), expectedDay, expectedDay + 1);
        return true;
    }

    /**
     * Core suppression starts on a new facility day but has its own admission
     * requirements. It still must use the same atomic day commit as normal play.
     */
    public static boolean beginSuppressionDay(ServerPlayer actor) {
        if (!isEligibleRealPlayer(actor)) return false;
        MinecraftServer server = actor.server;
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        int expectedDay = facility.getDay();
        if (!advanceActorDay(actor, expectedDay)) return false;
        if (completeFacilityDay(server, actor, expectedDay)) return true;
        actor.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(daily -> daily.setCurrentDay(expectedDay));
        return false;
    }

    private static boolean advanceActorDay(ServerPlayer actor, int expectedDay) {
        boolean[] advanced = {false};
        actor.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(daily -> {
            daily.setOwner(actor);
            daily.setCurrentDay(expectedDay);
            advanced[0] = daily.tryAdvanceToNextDay();
        });
        return advanced[0];
    }

    private static boolean completeFacilityDay(MinecraftServer server, ServerPlayer actor, int expectedDay) {
        int nextDay = expectedDay + 1;
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        // The old CompanyDayAdvanceEvent is cancellable. Only after its complete
        // listener chain accepts the transition may global facility state advance.
        FacilityManager.beginDay(actor.serverLevel(), nextDay);
        if (facility.getDay() != nextDay) return false;
        MinecraftForge.EVENT_BUS.post(new FacilityDayCommittedEvent(server, expectedDay, nextDay));

        for (ServerPlayer player : realCompanyPlayers(server)) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(daily -> {
                daily.setOwner(player);
                daily.setCurrentDay(nextDay);
                daily.setTodayWorkCount(0);
                if (daily.isArmorLocked()) daily.unlockArmor();
                daily.setHasSleep(false);
                MessageLoader.getLoader().sendToPlayer(player, new CompanyDailySyncPacket(
                        nextDay, 0, false, false));
            });
            if (!ItemUtil.hasItem(player, ModItems.ARMOR_LOCK.get())) {
                player.getInventory().add(new ItemStack(ModItems.ARMOR_LOCK.get()));
            }
            MinecraftForge.EVENT_BUS.post(new PlayerEnteredFacilityDayEvent(player, expectedDay, nextDay));
        }
        broadcast(server, Component.literal("DAY " + nextDay).withStyle(ChatFormatting.GOLD));
        FacilityManager.syncAll(server, facility);
        return true;
    }

    public static VoteView viewFor(ServerPlayer player, long gameTime) {
        DayVoteState vote = ACTIVE.get(player.server);
        if (vote == null) return VoteView.NONE;
        DayVoteState.Vote own = vote.voteOf(player.getUUID());
        return new VoteView(true, (int) Math.max(0L, vote.deadline() - gameTime),
                vote.eligible().size(), vote.approvalCount(), vote.rejectCount(),
                vote.requiredApprovals(), own == null ? 0 : own == DayVoteState.Vote.APPROVE ? 1 : -1);
    }

    public static boolean isReady(ServerPlayer player, FacilityDayData data) {
        return isEligibleRealPlayer(player) && DayCompletionRequirements.allSatisfied(player, data);
    }

    public static void clear(MinecraftServer server) {
        ACTIVE.remove(server);
    }

    private static void cancel(MinecraftServer server, String message) {
        ACTIVE.remove(server);
        broadcast(server, Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static ArrayList<ServerPlayer> realCompanyPlayers(MinecraftServer server) {
        ArrayList<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isEligibleRealPlayer(player)) result.add(player);
        }
        return result;
    }

    private static boolean isEligibleRealPlayer(ServerPlayer player) {
        return !(player instanceof EmployeeServerPlayer)
                && !player.getPersistentData().getBoolean("lobotocraft_ai_employee")
                && player.level().dimension() == ModDimensions.LOBOTO_KEY;
    }

    private static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : realCompanyPlayers(server)) player.displayClientMessage(message, false);
    }

    private static void reject(ServerPlayer player, String playerMessage, String logReason) {
        player.displayClientMessage(Component.literal(playerMessage).withStyle(ChatFormatting.RED), false);
        FacilityPatch.LOGGER.warn("Rejected end-day request from {}: {}",
                player.getGameProfile().getName(), logReason);
    }

    public record VoteView(boolean active, int remainingTicks, int eligible, int approvals,
                           int rejects, int required, int ownVote) {
        public static final VoteView NONE = new VoteView(false, 0, 0, 0, 0, 0, 0);
    }
}
