package com.wzz.lobotocraft.network;

import java.util.function.Supplier;

import com.wzz.lobotocraft.util.UnsafeUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class MessageCreater {

	final IMessage message;
	
	public MessageCreater(IMessage message) {
		this.message = message;
	}
	
	public static void toBuf(MessageCreater creater, FriendlyByteBuf buf) {
		String path = creater.message.getClass().getName();
		byte[] bytes = path.getBytes();
		buf.writeInt(bytes.length);
        for (byte b : bytes) {
            buf.writeByte(b);
        }
		creater.message.toBytes(buf);
	}

	public static MessageCreater fromBuf(FriendlyByteBuf buf) {
		byte[] bytes = new byte[buf.readInt()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = buf.readByte();
		}
		String path = new String(bytes);
		Class<?> clazz;
		try {
			clazz = Class.forName(path);
		} catch (Exception e) {
			throw new RuntimeException("Unknown message class: " + path, e);
		}
		IMessage message;
		try {
			message = (IMessage) UnsafeUtil.unsafe.allocateInstance(clazz);
		} catch (Throwable e) {
			try {
				message = (IMessage) clazz.getDeclaredConstructor().newInstance();
			} catch (Throwable e2) {
				throw new RuntimeException("Failed to create message instance: " + path, e2);
			}
		}
		message.fromBytes(buf);
		return new MessageCreater(message);
	}
	
	public static void run(MessageCreater creater, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context ct = ctx.get();
		ct.enqueueWork(() -> {
			if (!ct.getDirection().getReceptionSide().isClient() && !creater.message.sendToClient()) {
				creater.message.run(ct);
			} else {
				creater.message.runClient(ct);
			}
		});
		ct.setPacketHandled(true);
	}
}