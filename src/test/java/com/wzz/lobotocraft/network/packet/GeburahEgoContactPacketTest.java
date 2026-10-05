package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GeburahEgoContactPacketTest {
    private static final ResourceLocation DIM = new ResourceLocation("lobotocraft", "facility");
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    @Test
    void bothMovesRoundTripAndRemainClientboundOnly() {
        for (GeburahMove move : List.of(GeburahMove.SLASH, GeburahMove.STAB)) {
            var packet = new GeburahEgoContactPacket(DIM, move, FORWARD,
                    List.of(new Vec3(4, 72, -3), new Vec3(6, 72, -4)), 991);
            var encoded = new FriendlyByteBuf(Unpooled.buffer());
            var actual = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(encoded);
                byte[] expected = new byte[encoded.readableBytes()];
                encoded.getBytes(0, expected);
                var decoded = new GeburahEgoContactPacket();
                decoded.fromBytes(encoded);
                assertEquals(0, encoded.readableBytes());
                decoded.toBytes(actual);
                byte[] result = new byte[actual.readableBytes()];
                actual.readBytes(result);
                assertArrayEquals(expected, result);
                int id = PacketRegistry.idOf(GeburahEgoContactPacket.class);
                assertTrue(PacketRegistry.accepts(id, true));
                assertFalse(PacketRegistry.accepts(id, false));
            } finally {
                encoded.release();
                actual.release();
            }
        }
    }

    @Test
    void boundedContactsAndFiniteDirectionsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new GeburahEgoContactPacket(
                DIM, GeburahMove.STAB, FORWARD, Collections.nCopies(5, Vec3.ZERO), 0));
        assertThrows(IllegalArgumentException.class, () -> new GeburahEgoContactPacket(
                DIM, GeburahMove.VERTICAL_SLASH, FORWARD, List.of(Vec3.ZERO), 0));
        assertThrows(IllegalArgumentException.class, () -> new GeburahEgoContactPacket(
                DIM, GeburahMove.STAB, Vec3.ZERO, List.of(Vec3.ZERO), 0));
        assertThrows(IllegalArgumentException.class, () -> new GeburahEgoContactPacket(
                DIM, GeburahMove.STAB, FORWARD, List.of(new Vec3(Double.NaN, 0, 0)), 0));
    }

    @Test
    void malformedWireCountIsRejectedBeforeAllocation() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeResourceLocation(DIM);
            buf.writeVarInt(GeburahMove.STAB.ordinal());
            buf.writeDouble(0); buf.writeDouble(0); buf.writeDouble(1);
            buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> new GeburahEgoContactPacket().fromBytes(buf));
        } finally {
            buf.release();
        }
    }
}
