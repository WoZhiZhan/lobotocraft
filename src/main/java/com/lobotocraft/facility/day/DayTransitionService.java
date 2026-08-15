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
            reject(player, "message.lobotocraft.day.end.ineligible", "ineligible player");
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
            player.displayClientMessage(Component.translatable("message.lobotocraft.day.end.vote_active")
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
        broadcast(server, Component.translatable("message.lobotocraft.day.end.vote_started")
                .withStyle(ChatFormatting.AQUA));
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
            cancel(server, "message.lobotocraft.day.end.conditions_changed");
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
            broadcast(server, Component.translatable("message.lobotocraft.day.end.vote_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayer actor = realCompanyPlayers(server).stream()
                .filter(player -> vote.eligible().contains(player.getUUID()))
                .findFirst().orElse(null);
        if (actor == null) {
            broadcast(server, Component.translatable("message.lobotocraft.day.end.no_actor")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        commit(server, actor, vote.day());
    }

    private static boolean commit(MinecraftServer server, ServerPlayer actor, int expectedDay) {
        FacilityDayData facility = FacilityDayData.get(server.overworld());
        if (facility.getDay() != expectedDay) {
            reject(actor, "message.lobotocraft.day.end.day_changed",
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
            reject(actor, "message.lobotocraft.day.end.advance_cancelled",
                    "player capability missing or CompanyDayAdvanceEvent cancelled");
            return false;
        }
        if (!completeFacilityDay(server, actor, expectedDay)) {
            actor.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                    .ifPresent(daily -> daily.setCurrentDay(expectedDay));
            reject(actor, "message.lobotocraft.day.end.facility_failed",
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
        broadcast(server, Component.translatable("message.lobotocraft.day.started", nextDay)
                .withStyle(ChatFormatting.GOLD));
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

    private static void cancel(MinecraftServer server, String messageKey) {
        ACTIVE.remove(server);
        broadcast(server, Component.translatable(messageKey).withStyle(ChatFormatting.RED));
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

    private static void reject(ServerPlayer player, String playerMessageKey, String logReason) {
        player.displayClientMessage(Component.translatable(playerMessageKey).withStyle(ChatFormatting.RED), false);
        FacilityPatch.LOGGER.warn("Rejected end-day request from {}: {}",
                player.getGameProfile().getName(), logReason);
    }

    public record VoteView(boolean active, int remainingTicks, int eligible, int approvals,
                           int rejects, int required, int ownVote) {
        public static final VoteView NONE = new VoteView(false, 0, 0, 0, 0, 0, 0);
    }
}
