package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.ClientFacilityState;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.day.DayTransitionService;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record FacilityStatePacket(
    int day,
    int energy,
    int quota,
    int meltdownLevel,
    int gauge,
    int threshold,
    int activeMeltdowns,
    int earliestRemainingTicks,
    int nextOrdeal,
    int nextOrdealColor,
    long ordealScheduleTiers,
    long ordealScheduleColors,
    long notificationSerial,
    int notificationType,
    boolean dayReady,
    boolean voteActive,
    int voteRemainingTicks,
    int voteEligible,
    int voteApprovals,
    int voteRejects,
    int voteRequired,
    int ownVote,
    boolean paused,
    int pauseCooldownTicks,
    Map<UUID, Integer> meltdownRemainingTicks
) {
    static final int MAX_MELTDOWNS = 1024;

    public FacilityStatePacket {
        meltdownRemainingTicks = Map.copyOf(meltdownRemainingTicks);
    }

    public static FacilityStatePacket from(FacilityDayData data, long gameTime, ServerPlayer player) {
        Map<UUID, Integer> meltdowns = snapshotMeltdowns(data, gameTime);
        int remaining = meltdowns.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        DayTransitionService.VoteView vote = DayTransitionService.viewFor(player, gameTime);
        return new FacilityStatePacket(data.getDay(), data.getEnergy(), data.getEnergyQuota(),
            data.getMeltdownLevel(), data.getMeltdownGauge(), data.getMeltdownThreshold(),
            data.getActiveMeltdowns().size(), remaining, data.getNextOrdeal().ordinal(),
            data.getNextOrdealColor(), packScheduleTiers(data), packScheduleColors(data),
            data.getNotificationSerial(), data.getNotificationType(),
            DayTransitionService.isReady(player, data), vote.active(), vote.remainingTicks(),
            vote.eligible(), vote.approvals(), vote.rejects(), vote.required(), vote.ownVote(),
            com.lobotocraft.facility.FacilityPauseState.isPaused(player.server),
            com.lobotocraft.facility.FacilityPauseState.cooldownTicks(player.server), meltdowns);
    }

    public static Map<UUID, Integer> snapshotMeltdowns(FacilityDayData data, long gameTime) {
        Map<UUID, Integer> remaining = new LinkedHashMap<>();
        // Keep the earliest targets if a malformed/oversized save exceeds the wire limit.
        data.getActiveMeltdowns().entrySet().stream()
            .sorted(java.util.Comparator.comparingLong(entry ->
                entry.getValue().projectedRemainingTicks(gameTime)))
            .limit(MAX_MELTDOWNS)
            .forEach(entry -> remaining.put(entry.getKey(), (int) Math.max(0L,
                Math.min(Integer.MAX_VALUE, entry.getValue().projectedRemainingTicks(gameTime)))));
        return Map.copyOf(remaining);
    }

    public static void encode(FacilityStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.day);
        buffer.writeVarInt(packet.energy);
        buffer.writeVarInt(packet.quota);
        buffer.writeVarInt(packet.meltdownLevel);
        buffer.writeVarInt(packet.gauge);
        buffer.writeVarInt(packet.threshold);
        buffer.writeVarInt(packet.activeMeltdowns);
        buffer.writeVarInt(packet.earliestRemainingTicks);
        buffer.writeVarInt(packet.nextOrdeal);
        buffer.writeInt(packet.nextOrdealColor);
        buffer.writeLong(packet.ordealScheduleTiers);
        buffer.writeLong(packet.ordealScheduleColors);
        buffer.writeLong(packet.notificationSerial);
        buffer.writeVarInt(packet.notificationType);
        buffer.writeBoolean(packet.dayReady);
        buffer.writeBoolean(packet.voteActive);
        buffer.writeVarInt(packet.voteRemainingTicks);
        buffer.writeVarInt(packet.voteEligible);
        buffer.writeVarInt(packet.voteApprovals);
        buffer.writeVarInt(packet.voteRejects);
        buffer.writeVarInt(packet.voteRequired);
        buffer.writeByte(packet.ownVote);
        buffer.writeBoolean(packet.paused);
        buffer.writeVarInt(packet.pauseCooldownTicks);
        int count = Math.min(packet.meltdownRemainingTicks.size(), MAX_MELTDOWNS);
        buffer.writeVarInt(count);
        int written = 0;
        for (Map.Entry<UUID, Integer> entry : packet.meltdownRemainingTicks.entrySet()) {
            if (written++ >= count) break;
            buffer.writeUUID(entry.getKey());
            buffer.writeVarInt(Math.max(0, entry.getValue()));
        }
    }

    public static FacilityStatePacket decode(FriendlyByteBuf buffer) {
        return new FacilityStatePacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(), buffer.readLong(), buffer.readLong(),
            buffer.readLong(), buffer.readVarInt(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(),
            buffer.readBoolean(), buffer.readVarInt(), readMeltdowns(buffer));
    }

    private static Map<UUID, Integer> readMeltdowns(FriendlyByteBuf buffer) {
        int count = PacketDecodeLimits.readVarIntCount(buffer, MAX_MELTDOWNS, "facility meltdown");
        Map<UUID, Integer> remaining = new LinkedHashMap<>(count);
        for (int index = 0; index < count; index++) {
            UUID target = buffer.readUUID();
            if (remaining.put(target, Math.max(0, buffer.readVarInt())) != null) {
                throw new IllegalArgumentException("Duplicate facility meltdown target: " + target);
            }
        }
        return remaining;
    }

    public static void handle(FacilityStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientFacilityState.update(packet)));
        context.setPacketHandled(true);
    }

    public OrdealTier scheduledTier(int stage) {
        if (stage < 0 || stage > 15) return OrdealTier.NONE;
        return OrdealTier.byId((int) ((ordealScheduleTiers >>> (stage * 4)) & 0xFL));
    }

    public int scheduledColor(int stage) {
        if (stage < 0 || stage > 15) return 0;
        return (int) ((ordealScheduleColors >>> (stage * 3)) & 0x7L);
    }

    private static long packScheduleTiers(FacilityDayData data) {
        long packed = 0L;
        for (int stage = 0; stage <= 9; stage++) {
            packed |= ((long) data.getScheduledOrdeal(stage).ordinal() & 0xFL) << (stage * 4);
        }
        return packed;
    }

    private static long packScheduleColors(FacilityDayData data) {
        long packed = 0L;
        for (int stage = 0; stage <= 9; stage++) {
            packed |= ((long) data.getScheduledOrdealColor(stage) & 0x7L) << (stage * 3);
        }
        return packed;
    }
}
