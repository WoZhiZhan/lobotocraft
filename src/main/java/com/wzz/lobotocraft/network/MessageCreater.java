package com.wzz.lobotocraft.network;

import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.util.UnsafeUtil;
import io.netty.buffer.Unpooled;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class MessageCreater {
    // Room and employee snapshots are bounded well below this size. Keeping
    // the envelope small limits malformed-packet allocation before dispatch.
    private static final int MAX_PAYLOAD_BYTES = 128 * 1024;

    final IMessage message;
    private final int deferredPacketId;
    private final byte[] deferredPayload;

    public MessageCreater(IMessage message) {
        this.message = message;
        this.deferredPacketId = -1;
        this.deferredPayload = null;
    }

    private MessageCreater(int packetId, byte[] payload) {
        this.message = null;
        this.deferredPacketId = packetId;
        this.deferredPayload = payload;
    }

    public static void toBuf(MessageCreater creater, FriendlyByteBuf buf) {
        buf.writeVarInt(PacketRegistry.idOf(creater.message.getClass()));
        creater.message.toBytes(buf);
    }

    /**
     * Decoding happens before NetworkEvent.Context exposes the reception side.
     * Preserve the payload as bounded bytes here; only deserialize its fields
     * after the direction whitelist has been checked in run().
     */
    public static MessageCreater fromBuf(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        if (PacketRegistry.byId(id) == null) {
            throw new IllegalStateException("Unknown packet id " + id);
        }
        int payloadLength = buf.readableBytes();
        if (payloadLength > MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("Packet payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        byte[] payload = new byte[payloadLength];
        buf.readBytes(payload);
        return new MessageCreater(id, payload);
    }

    private IMessage decodeAfterDirectionCheck() {
        Class<? extends IMessage> clazz = PacketRegistry.byId(deferredPacketId);
        if (clazz == null) throw new IllegalStateException("Unknown packet id " + deferredPacketId);
        IMessage decoded;
        try {
            decoded = clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            try {
                decoded = (IMessage) UnsafeUtil.unsafe.allocateInstance(clazz);
            } catch (Throwable ignored) {
                throw new RuntimeException("Failed to create packet " + clazz.getName(), e);
            }
        }
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(deferredPayload));
        try {
            decoded.fromBytes(payload);
            if (payload.readableBytes() != 0) {
                throw new IllegalArgumentException("Packet " + clazz.getSimpleName()
                        + " left " + payload.readableBytes() + " unread bytes");
            }
            return decoded;
        } finally {
            payload.release();
        }
    }

    public static void run(MessageCreater creater, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            boolean receivedOnClient = context.getDirection().getReceptionSide().isClient();
            int packetId = creater.message == null ? creater.deferredPacketId
                    : PacketRegistry.idOf(creater.message.getClass());
            if (!PacketRegistry.accepts(packetId, receivedOnClient)) {
                ModLogger.LOGGER.warn("Rejected packet id {} on invalid reception side", packetId);
                return;
            }

            IMessage message;
            try {
                message = creater.message != null ? creater.message : creater.decodeAfterDirectionCheck();
            } catch (RuntimeException e) {
                ModLogger.LOGGER.warn("Rejected malformed packet id {}", packetId, e);
                return;
            }
            if (receivedOnClient) message.runClient(context);
            else message.run(context);
        });
        context.setPacketHandled(true);
    }
}
