package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.entity.control.WolfSwallowControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Authoritative rules shared by direct player work and AI work dispatch.
 *
 * <p>Registration is deliberately based on the containment marker's bound
 * instance UUID, rather than only on an abnormality code.  This prevents a
 * stale or unregistered entity (including a manually spawned one) from being
 * worked before the containment room has adopted it.</p>
 */
public final class AbnormalityWorkRules {
    private AbnormalityWorkRules() {
    }

    /** Returns whether the target is currently registered by its containment marker. */
    public static boolean isRegistered(AbstractAbnormality abnormality) {
        if (abnormality == null || abnormality.isRemoved() || abnormality.level() == null
                || !abnormality.canBeContained()) return false;
        ResourceLocation dimension = abnormality.getHomeRoomDimension();
        BlockPos markerPos = abnormality.getHomeRoomMarker();
        if (dimension == null || markerPos == null || abnormality.getServer() == null) return false;

        ServerLevel homeLevel = abnormality.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, dimension));
        if (homeLevel == null) return false;
        // A room marker may be in an unloaded facility chunk after a restart.
        // Loading this single authoritative marker avoids a false denial while
        // keeping the check independent of a broad entity scan.
        if (!homeLevel.hasChunkAt(markerPos)) homeLevel.getChunkAt(markerPos);
        if (!(homeLevel.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)) return false;

        return isRegisteredBinding(marker.getBoundAbnormality(), marker.getBoundCode(),
                abnormality.getUUID(), abnormality.getAbnormalityCode());
    }

    static boolean isRegisteredBinding(UUID boundId, String boundCode,
                                       UUID targetId, String targetCode) {
        if (boundId == null || targetId == null || !boundId.equals(targetId)) return false;
        return boundCode == null || boundCode.isBlank() || boundCode.equals(targetCode);
    }

    public static String registrationDenial(AbstractAbnormality abnormality) {
        return isRegistered(abnormality)
                ? null : "该异想体尚未登记，无法进行工作。";
    }

    /** Returns the authoritative denial for transient states that forbid work. */
    public static Component controlStateDenial(ServerPlayer player) {
        if (WolfSwallowControl.isActive(player)) {
            return Component.translatable("message.lobotocraft.work.reason.wolf_swallowed");
        }
        if (UniqueWorkRequest.isControlled(player)) {
            return Component.translatable("message.lobotocraft.sacrifice.busy");
        }
        if (EntitySnowQueen.isFrozen(player)) {
            return Component.translatable("message.lobotocraft.work.reason.frozen");
        }
        if (HostileControlState.isActive(player)) {
            return Component.translatable("message.lobotocraft.work.reason.red_shoes");
        }
        return null;
    }

    /** Only AI employees participate in the one-AI-per-abnormality reservation. */
    public static boolean isAiEmployee(ServerPlayer player) {
        return player instanceof EmployeeServerPlayer;
    }

    static boolean hasAiReservationConflict(boolean actorIsAi, boolean anotherAiIsWorking) {
        return actorIsAi && anotherAiIsWorking;
    }

    public static boolean canReplacePendingAiOrder(boolean formalWorkActive) {
        return !formalWorkActive;
    }
}
