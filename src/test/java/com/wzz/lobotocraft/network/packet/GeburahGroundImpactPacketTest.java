package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GeburahGroundImpactPacketTest {
    @Test
    void packetRoundTripAndClientOnlyWhitelist() {
        var source = new GeburahGroundImpactPacket(new ResourceLocation("lobotocraft", "facility"),
                new Vec3(1.25D, 72.0D, -5.5D), 2.2F, 823);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf roundTrip = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.toBytes(encoded);
            byte[] expected = new byte[encoded.readableBytes()];
            encoded.getBytes(0, expected);
            var decoded = new GeburahGroundImpactPacket();
            decoded.fromBytes(encoded);
            assertEquals(0, encoded.readableBytes());
            decoded.toBytes(roundTrip);
            byte[] actual = new byte[roundTrip.readableBytes()];
            roundTrip.readBytes(actual);
            assertArrayEquals(expected, actual);
            int id = PacketRegistry.idOf(GeburahGroundImpactPacket.class);
            assertTrue(PacketRegistry.accepts(id, true));
            assertFalse(PacketRegistry.accepts(id, false));
        } finally {
            encoded.release();
            roundTrip.release();
        }
    }

    @Test
    void rejectsNonFiniteAndOversizedPayloadValues() {
        var dimension = new ResourceLocation("lobotocraft", "facility");
        assertThrows(IllegalArgumentException.class, () -> new GeburahGroundImpactPacket(
                dimension, new Vec3(Double.NaN, 0, 0), 2.2F, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeburahGroundImpactPacket(
                dimension, Vec3.ZERO, Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeburahGroundImpactPacket(
                dimension, Vec3.ZERO, 20.0F, 0));
    }
}
