package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.LockInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class CombatControlLockService {
    private static final Map<ServerPlayer, LockData> LOCKS = new WeakHashMap<>();

    private CombatControlLockService() {
    }

    public static void lock(ServerPlayer player, LivingEntity source, int durationTicks) {
        if (player == null || source == null || durationTicks <= 0 || !player.isAlive()) return;
        LOCKS.put(player, new LockData(source, player.position(),
                player.level().getGameTime() + durationTicks));
        enforce(player, LOCKS.get(player));
    }

    public static void tick(ServerPlayer player) {
        LockData data = LOCKS.get(player);
        if (data == null) return;
        if (!player.isAlive() || data.source().isRemoved() || !data.source().isAlive()
                || data.source().level() != player.level()
                || player.level().getGameTime() >= data.expiresAt()) {
            clear(player);
            return;
        }
        enforce(player, data);
    }

    public static boolean isLocked(ServerPlayer player) {
        return player != null && LOCKS.containsKey(player);
    }

    public static void clear(ServerPlayer player) {
        if (player != null) LOCKS.remove(player);
    }

    public static void clearLocksFrom(LivingEntity source) {
        LOCKS.entrySet().removeIf(entry -> entry.getValue().source() == source);
    }

    private static void enforce(ServerPlayer player, LockData data) {
        Vec3 anchor = data.anchor();
        if (player.position().distanceToSqr(anchor) > 1.0E-4D) {
            player.connection.teleport(anchor.x, anchor.y, anchor.z,
                    player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.stopUsingItem();
        MessageLoader.getLoader().sendToPlayer(player, new LockInputPacket());
    }

    private record LockData(LivingEntity source, Vec3 anchor, long expiresAt) {
    }
}
