package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.ModMain;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-issued, single-use permission to submit an abnormality interaction
 * after the server has accepted opening its corresponding screen.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class WorkStartAuthorizations {
    static final long TTL_TICKS = 20L * 120L;
    private static final Map<MinecraftServer, Map<UUID, Authorization>> ACTIVE = new IdentityHashMap<>();

    private WorkStartAuthorizations() {
    }

    public static void grant(ServerPlayer player, UUID abnormalityId) {
        grant(player, abnormalityId, Interaction.NORMAL_WORK);
    }

    public static void grantToolUse(ServerPlayer player, UUID abnormalityId) {
        grant(player, abnormalityId, Interaction.TOOL_USE);
    }

    private static void grant(ServerPlayer player, UUID abnormalityId, Interaction interaction) {
        if (player == null || abnormalityId == null) return;
        ACTIVE.computeIfAbsent(player.server, ignored -> new HashMap<>()).put(
                player.getUUID(), new Authorization(abnormalityId,
                        player.serverLevel().dimension().location().toString(),
                        player.serverLevel().getGameTime() + TTL_TICKS, interaction));
    }

    public static boolean consume(ServerPlayer player, UUID abnormalityId) {
        return consume(player, abnormalityId, Interaction.NORMAL_WORK);
    }

    public static boolean consumeToolUse(ServerPlayer player, UUID abnormalityId) {
        return consume(player, abnormalityId, Interaction.TOOL_USE);
    }

    private static boolean consume(ServerPlayer player, UUID abnormalityId, Interaction interaction) {
        if (player == null || abnormalityId == null) return false;
        Map<UUID, Authorization> authorizations = ACTIVE.get(player.server);
        if (authorizations == null) return false;
        Authorization authorization = authorizations.remove(player.getUUID());
        return authorization != null && matches(authorization.abnormalityId, authorization.dimension,
                authorization.expiresAt, authorization.interaction, abnormalityId,
                player.serverLevel().dimension().location().toString(),
                player.serverLevel().getGameTime(), interaction);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            revoke(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) revoke(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) revoke(player);
    }

    private static void revoke(ServerPlayer player) {
        Map<UUID, Authorization> authorizations = ACTIVE.get(player.server);
        if (authorizations == null) return;
        authorizations.remove(player.getUUID());
        if (authorizations.isEmpty()) ACTIVE.remove(player.server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.remove(event.getServer());
    }

    static boolean matches(UUID grantedAbnormalityId, String grantedDimension, long expiresAt,
                           Interaction grantedInteraction, UUID requestedAbnormalityId,
                           String requestedDimension, long currentGameTime,
                           Interaction requestedInteraction) {
        return grantedAbnormalityId != null && grantedDimension != null
                && grantedInteraction != null && requestedAbnormalityId != null
                && requestedDimension != null && requestedInteraction != null
                && currentGameTime <= expiresAt
                && grantedAbnormalityId.equals(requestedAbnormalityId)
                && grantedDimension.equals(requestedDimension)
                && grantedInteraction == requestedInteraction;
    }

    enum Interaction {
        NORMAL_WORK,
        TOOL_USE
    }

    private record Authorization(UUID abnormalityId, String dimension, long expiresAt, Interaction interaction) {
    }
}
