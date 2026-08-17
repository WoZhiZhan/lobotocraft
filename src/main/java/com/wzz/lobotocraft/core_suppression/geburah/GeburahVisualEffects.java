package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.GeburahVisualEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GeburahVisualEffects {
    private GeburahVisualEffects() {
    }

    public static void sendHit(ServerPlayer player, EntityGeburah source,
                               GeburahVisualEffectType type, Vec3 direction,
                               float intensity, float actualHealthLoss,
                               int durationTicks) {
        if (player == null || source == null || type == null) return;
        float severity = GeburahHitFeedbackPolicy.severity(actualHealthLoss,
                player.getMaxHealth(), intensity);
        send(player, source, type, direction, intensity, severity, durationTicks);
    }

    public static void broadcastStage(EntityGeburah source, GeburahVisualEffectType type,
                                      float intensity, int durationTicks) {
        if (source == null || type == null || !(source.level() instanceof ServerLevel level)) return;
        ServerLevel facility = level.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) return;
        GeburahEncounterData data = GeburahEncounterData.get(facility);
        Set<UUID> audience = new HashSet<>(data.participants());
        audience.addAll(data.observers());
        Vec3 direction = Vec3.directionFromRotation(0.0F, source.getYRot());
        for (UUID playerId : audience) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) send(player, source, type, direction,
                    intensity, 0.0F, durationTicks);
        }
    }

    private static void send(ServerPlayer player, EntityGeburah source,
                             GeburahVisualEffectType type, Vec3 direction,
                             float intensity, float hitSeverity, int durationTicks) {
        MessageLoader.getLoader().sendToPlayer(player, new GeburahVisualEffectPacket(
                type, source.position().add(0.0D, source.getBbHeight() * 0.55D, 0.0D),
                direction, intensity, hitSeverity,
                source.syncedPhase() == GeburahPhase.EGO,
                durationTicks, source.getRandom().nextInt()));
    }
}
