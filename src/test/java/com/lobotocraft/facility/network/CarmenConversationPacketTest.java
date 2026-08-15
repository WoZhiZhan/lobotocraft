package com.lobotocraft.facility.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class CarmenConversationPacketTest {
    @Test
    void roundTripPreservesNestedTranslationComponents() {
        Component message = Component.translatable("message.lobotocraft.carmen.escape.0",
            Component.translatable("entity.lobotocraft.onebad"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CarmenConversationPacket.encode(new CarmenConversationPacket(message), buffer);
            CarmenConversationPacket decoded = CarmenConversationPacket.decode(buffer);

            TranslatableContents outer = assertInstanceOf(
                TranslatableContents.class, decoded.message().getContents());
            assertEquals("message.lobotocraft.carmen.escape.0", outer.getKey());
            Component subject = assertInstanceOf(Component.class, outer.getArgs()[0]);
            TranslatableContents inner = assertInstanceOf(
                TranslatableContents.class, subject.getContents());
            assertEquals("entity.lobotocraft.onebad", inner.getKey());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
