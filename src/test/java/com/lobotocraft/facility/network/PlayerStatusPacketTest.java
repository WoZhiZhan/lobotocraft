package com.lobotocraft.facility.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class PlayerStatusPacketTest {
    @Test
    void encoderTruncatesAndDecoderConsumesTheWholePacket() {
        List<PlayerStatusPacket.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 1025; i++) entries.add(entry(i));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerStatusPacket.encode(new PlayerStatusPacket(entries), buffer);
            PlayerStatusPacket decoded = PlayerStatusPacket.decode(buffer);
            assertEquals(1024, decoded.entries().size());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsOversizedCountInsteadOfPartiallyReadingIt() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1025);
            assertThrows(IllegalArgumentException.class, () -> PlayerStatusPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripPreservesLocalizedNavigationTarget() {
        PlayerStatusPacket.Entry entry = new PlayerStatusPacket.Entry(
            UUID.randomUUID(), 3,
            Component.translatable("status.lobotocraft.navigation.work",
                Component.translatable("work_type.lobotocraft.instinct"),
                Component.translatable("entity.lobotocraft.onebad")),
            20.0F, 20.0F, 20.0F, 20.0F);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerStatusPacket.encode(new PlayerStatusPacket(List.of(entry)), buffer);
            Component target = PlayerStatusPacket.decode(buffer).entries().get(0).workTarget();
            TranslatableContents translated = assertInstanceOf(
                TranslatableContents.class, target.getContents());
            assertEquals("status.lobotocraft.navigation.work", translated.getKey());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static PlayerStatusPacket.Entry entry(int index) {
        return new PlayerStatusPacket.Entry(new UUID(0L, index + 1L), 0, Component.empty(), 20.0F,
                20.0F, 20.0F, 20.0F);
    }
}
