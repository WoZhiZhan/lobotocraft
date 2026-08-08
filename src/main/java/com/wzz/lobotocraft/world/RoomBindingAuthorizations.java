package com.wzz.lobotocraft.world;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Short-lived server-issued authority for changing a containment room binding.
 * A client can only bind one of the candidates it just requested for that room;
 * arbitrary UUIDs from edited packets are not accepted.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class RoomBindingAuthorizations {
    private static final long LIFETIME_TICKS = 200L;
    private static final Map<MinecraftServer, Map<UUID, Authorization>> ACTIVE = new HashMap<>();

    private RoomBindingAuthorizations() {
    }

    public static void issue(ServerPlayer player, ResourceLocation dimension, BlockPos roomPos,
                             Iterable<UUID> candidates) {
        Set<UUID> allowed = new HashSet<>();
        for (UUID candidate : candidates) if (candidate != null) allowed.add(candidate);
        authorizations(player.server).put(player.getUUID(), new Authorization(dimension, roomPos.immutable(), allowed,
                player.serverLevel().getGameTime() + LIFETIME_TICKS));
    }

    public static boolean consume(ServerPlayer player, ResourceLocation dimension, BlockPos roomPos,
                                  UUID candidate) {
        Map<UUID, Authorization> active = authorizations(player.server);
        Authorization authorization = active.get(player.getUUID());
        if (authorization == null || player.serverLevel().getGameTime() > authorization.expiresAt
                || !authorization.dimension.equals(dimension) || !authorization.roomPos.equals(roomPos)
                || (candidate != null && !authorization.candidates.contains(candidate))) {
            return false;
        }
        active.remove(player.getUUID());
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            authorizations(player.server).remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.remove(event.getServer());
    }

    private static Map<UUID, Authorization> authorizations(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    private record Authorization(ResourceLocation dimension, BlockPos roomPos, Set<UUID> candidates,
                                 long expiresAt) {
    }
}
