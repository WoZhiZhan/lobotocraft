package com.wzz.lobotocraft.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestFacilityManagementPacketTest {
    @Test
    void returnNavigationFlagsSurviveSerialization() {
        assertRoundTrip(RequestFacilityManagementPacket.returnToFacility(), false, true);
        assertRoundTrip(RequestFacilityManagementPacket.returnToEgoAssignment(), true, true);
        assertRoundTrip(new RequestFacilityManagementPacket(false), false, false);
        assertRoundTrip(new RequestFacilityManagementPacket(true), true, false);
    }

    private static void assertRoundTrip(RequestFacilityManagementPacket source,
                                        boolean ego, boolean navigation) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.toBytes(buffer);
            RequestFacilityManagementPacket decoded = new RequestFacilityManagementPacket();
            decoded.fromBytes(buffer);
            assertEquals(ego, decoded.opensEgoAssignment());
            if (navigation) assertTrue(decoded.isReturnNavigation());
            else assertFalse(decoded.isReturnNavigation());
        } finally {
            buffer.release();
        }
    }
}
