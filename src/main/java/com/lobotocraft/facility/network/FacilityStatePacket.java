package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.ClientFacilityState;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.day.DayTransitionService;
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
    int ownVote
) {
    public static FacilityStatePacket from(FacilityDayData data, long gameTime, ServerPlayer player) {
        long earliest = data.getActiveMeltdowns().values().stream()
            .mapToLong(FacilityDayData.ActiveMeltdown::deadline).min().orElse(gameTime);
        int remaining = data.getActiveMeltdowns().isEmpty() ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, earliest - gameTime));
        DayTransitionService.VoteView vote = DayTransitionService.viewFor(player, gameTime);
        return new FacilityStatePacket(data.getDay(), data.getEnergy(), data.getEnergyQuota(),
            data.getMeltdownLevel(), data.getMeltdownGauge(), data.getMeltdownThreshold(),
            data.getActiveMeltdowns().size(), remaining, data.getNextOrdeal().ordinal(),
            data.getNextOrdealColor(), packScheduleTiers(data), packScheduleColors(data),
            data.getNotificationSerial(), data.getNotificationType(),
            DayTransitionService.isReady(player, data), vote.active(), vote.remainingTicks(),
            vote.eligible(), vote.approvals(), vote.rejects(), vote.required(), vote.ownVote());
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
    }

    public static FacilityStatePacket decode(FriendlyByteBuf buffer) {
        return new FacilityStatePacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(), buffer.readLong(), buffer.readLong(),
            buffer.readLong(), buffer.readVarInt(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByte());
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
