package com.wzz.lobotocraft.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wzz.lobotocraft.network.packet.RequestLlmConfigPacket;
import io.netty.buffer.Unpooled;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MessageCreaterTest {
    @Test
    void wellFormedPayloadIsDecoded() throws Exception {
        MessageCreater encoded = envelope(false);
        assertInstanceOf(RequestLlmConfigPacket.class, decode(encoded));
    }

    @Test
    void trailingPayloadBytesAreRejected() throws Exception {
        MessageCreater encoded = envelope(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> decode(encoded));
        IllegalArgumentException cause = assertInstanceOf(
                IllegalArgumentException.class, exception.getCause());
        assertEquals("Packet RequestLlmConfigPacket left 1 unread bytes", cause.getMessage());
    }

    private static MessageCreater envelope(boolean trailingByte) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(PacketRegistry.idOf(RequestLlmConfigPacket.class));
            if (trailingByte) buffer.writeByte(0x7F);
            return MessageCreater.fromBuf(buffer);
        } finally {
            buffer.release();
        }
    }

    private static IMessage decode(MessageCreater encoded) throws Exception {
        Method method = MessageCreater.class.getDeclaredMethod("decodeAfterDirectionCheck");
        method.setAccessible(true);
        return (IMessage) method.invoke(encoded);
    }
}
