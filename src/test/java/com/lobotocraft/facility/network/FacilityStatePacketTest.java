package com.lobotocraft.facility.network;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownCause;
import com.lobotocraft.facility.data.MeltdownLifecycle;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityStatePacketTest {
    @Test
    void dynamicRemainingUsesTargetIdentityEvenWhenLocatorIsUnknown() {
        FacilityDayData data = new FacilityDayData();
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        MeltdownLifecycle.start(data, first, 100, 2, "O-03-89", MeltdownCause.QLIPHOTH_OVERLOAD);
        MeltdownLifecycle.start(data, second, 100, 2, "O-03-89", MeltdownCause.APOCALYPSE_BIRD_EGG);
        MeltdownLifecycle.setTimeRate(data, second, 100, 2000);
        MeltdownLifecycle.advance(data, 120);
        assertFalse(data.getActiveMeltdowns().get(first).targetLocator().isKnown());
        assertFalse(data.getActiveMeltdowns().get(second).targetLocator().isKnown());

        Map<UUID, Integer> remaining = FacilityStatePacket.snapshotMeltdowns(data, 120);

        assertEquals(Map.of(first, 1480, second, 730), remaining);
        assertEquals(730, remaining.values().stream().mapToInt(Integer::intValue).min().orElseThrow());
        data.setPaused(true);
        assertEquals(remaining, FacilityStatePacket.snapshotMeltdowns(data, 1120));
        assertEquals(120, data.getActiveMeltdowns().get(first).lastProcessedTick());
    }

    @Test
    void serverSnapshotIsBoundedAndRetainsTheEarliestTarget() {
        FacilityDayData data = new FacilityDayData();
        for (int index = 1; index <= 1025; index++) {
            data.addMeltdown(new UUID(0, index), 100L + index, 2, "same code");
        }

        Map<UUID, Integer> remaining = FacilityStatePacket.snapshotMeltdowns(data, 100);

        assertEquals(1024, remaining.size());
        assertEquals(1, remaining.get(new UUID(0, 1)).intValue());
        assertFalse(remaining.containsKey(new UUID(0, 1025)));
    }

    @Test
    void oversizedEncodeIsBoundedAndLeavesTheFollowingMessageAligned() {
        Map<UUID, Integer> remaining = new LinkedHashMap<>();
        for (int index = 0; index < 1025; index++) remaining.put(new UUID(0, index), 119);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityStatePacket.encode(packet(remaining), buffer);
            buffer.writeInt(0x12345678);

            FacilityStatePacket decoded = FacilityStatePacket.decode(buffer);

            assertEquals(1024, decoded.meltdownRemainingTicks().size());
            assertTrue(decoded.meltdownRemainingTicks().values().stream().allMatch(ticks -> ticks == 119));
            assertEquals(0x12345678, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1025, Integer.MAX_VALUE})
    void invalidCollectionCountIsRejectedBeforeReadingEntries(int count) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityStatePacket.encode(packet(Map.of()), buffer);
            buffer.writerIndex(buffer.writerIndex() - 1);
            buffer.writeVarInt(count);

            assertThrows(IllegalArgumentException.class, () -> FacilityStatePacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void duplicateTargetsAreRejectedRatherThanSilentlyOverwritingTime() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityStatePacket.encode(packet(Map.of()), buffer);
            buffer.writerIndex(buffer.writerIndex() - 1);
            buffer.writeVarInt(2);
            for (int ticks : new int[]{119, 79}) {
                buffer.writeUUID(new UUID(0, 1));
                buffer.writeVarInt(ticks);
            }

            assertThrows(IllegalArgumentException.class, () -> FacilityStatePacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static FacilityStatePacket packet(Map<UUID, Integer> remaining) {
        return new FacilityStatePacket(6, 10, 100, 2, 1, 4, remaining.size(), 119,
                0, 0, 0L, 0L, 0L, 0,
                false, false, 0, 0, 0, 0, 0, 0, false, 0, remaining);
    }
}
