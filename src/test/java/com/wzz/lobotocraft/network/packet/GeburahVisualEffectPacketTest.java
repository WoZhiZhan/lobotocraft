package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class GeburahVisualEffectPacketTest {
    @Test
    void wireFormatPreservesHitSeverity() {
        GeburahVisualEffectPacket original = new GeburahVisualEffectPacket(
                GeburahVisualEffectType.BLOOD_MIST_HIT,
                new Vec3(1.25D, 2.5D, -3.75D), new Vec3(0.6D, 0.0D, -0.8D),
                1.0F, 0.73F, true, 12, 14526);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        original.toBytes(encoded);
        byte[] expected = new byte[encoded.readableBytes()];
        encoded.getBytes(encoded.readerIndex(), expected);

        GeburahVisualEffectPacket decoded = new GeburahVisualEffectPacket();
        decoded.fromBytes(new FriendlyByteBuf(Unpooled.wrappedBuffer(expected)));
        FriendlyByteBuf roundTrip = new FriendlyByteBuf(Unpooled.buffer());
        decoded.toBytes(roundTrip);
        byte[] actual = new byte[roundTrip.readableBytes()];
        roundTrip.getBytes(roundTrip.readerIndex(), actual);

        assertArrayEquals(expected, actual);
    }
}
