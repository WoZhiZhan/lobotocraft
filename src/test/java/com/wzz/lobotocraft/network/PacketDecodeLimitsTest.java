package com.wzz.lobotocraft.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PacketDecodeLimitsTest {
    @Test
    void intCountsAreRejectedBeforeUse() {
        assertIntCountRejected(-1);
        assertIntCountRejected(Integer.MAX_VALUE);
    }

    @Test
    void varIntCountsAreRejectedBeforeUse() {
        assertVarIntCountRejected(-1);
        assertVarIntCountRejected(Integer.MAX_VALUE);
    }

    @Test
    void boundaryCountsAreAccepted() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(16);
            buffer.writeVarInt(16);
            assertEquals(16, PacketDecodeLimits.readIntCount(buffer, 16, "test"));
            assertEquals(16, PacketDecodeLimits.readVarIntCount(buffer, 16, "test"));
        } finally {
            buffer.release();
        }
    }

    private static void assertIntCountRejected(int value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(value);
            assertThrows(IllegalArgumentException.class,
                    () -> PacketDecodeLimits.readIntCount(buffer, 16, "test"));
        } finally {
            buffer.release();
        }
    }

    private static void assertVarIntCountRejected(int value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(value);
            assertThrows(IllegalArgumentException.class,
                    () -> PacketDecodeLimits.readVarIntCount(buffer, 16, "test"));
        } finally {
            buffer.release();
        }
    }
}
