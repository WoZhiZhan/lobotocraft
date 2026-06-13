package com.wzz.lobotocraft.network;

import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.network.packet.LambdaPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Consumer;

public class MessageLoader {
	
	private static MessageLoader loader;
	
	public static MessageLoader getLoader() {
		if (loader == null) {
			loader = new MessageLoader();
			loader.load();
		}
		return loader;
	}
	
	public final SimpleChannel instance;
	private static final String PROTOCOL_VERSION = "2";
	
	public int id = 0;
	
	private MessageLoader() {
		instance = NetworkRegistry.newSimpleChannel(ResourceUtil.createInstance("main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
	}

	public void sendToPlayer(ServerPlayer player, IMessage message) {
		if (!message.sendToClient()) {
			throw new IllegalStateException("包 " + message.getClass().getName() + " 不是 clientbound！");
		}
		instance.send(PacketDistributor.PLAYER.with(() -> player), new MessageCreater(message));
	}

	public void sendToServer(IMessage message) {
		if (!message.sendToServer()) {
			throw new IllegalStateException("包 " + message.getClass().getName() + " 不是 serverbound！");
		}
		instance.sendToServer(new MessageCreater(message));
	}

	public void sendToServer(ServerPlayer player, Class<?> message) {
		try {
			instance.send(PacketDistributor.PLAYER.with(()->player), new MessageCreater((IMessage) message.newInstance()));
		} catch (InstantiationException | IllegalAccessException e) {
			ModLogger.LOGGER.error("发送包到服务器发生错误 ", e);
		}
    }
	
	public void sendToServer(Class<?> message) {
		try {
			instance.sendToServer(new MessageCreater((IMessage) message.newInstance()));
		} catch (InstantiationException | IllegalAccessException e) {
			ModLogger.LOGGER.error("发送包到服务器发生错误 ", e);
		}
    }
	
	private void load() {
		instance.messageBuilder(MessageCreater.class, id++)
				.encoder(MessageCreater::toBuf)
				.decoder(MessageCreater::fromBuf)
				.consumerMainThread(MessageCreater::run)
				.add();
	}

	public void sendClientMessage(ServerPlayer player, IMessage msg) {
		if (!msg.sendToClient())
			throw new IllegalStateException("该消息不是发往客户端的！");
		instance.send(PacketDistributor.PLAYER.with(() -> player), new MessageCreater(msg));
	}

	public void sendServerMessage(IMessage msg) {
		if (!msg.sendToServer())
			throw new IllegalStateException("该消息不是发往服务器的！");
		instance.sendToServer(new MessageCreater(msg));
	}

	public void sendToPlayer(ServerPlayer player, Consumer<CompoundTag> logic) {
		String actionId = UUID.randomUUID().toString();
		CompoundTag tag = new CompoundTag();
		logic.accept(tag);
		LambdaPacketHandler.registerClient(actionId, (data, mc) -> logic.accept(data));
		sendToPlayer(player, new LambdaPacket(actionId, tag));
	}

	public void sendToServer(ServerLambda lambda) {
		String actionId = UUID.randomUUID().toString();
		CompoundTag tag = new CompoundTag();
		LambdaPacketHandler.registerServer(actionId, lambda);
		sendToServer(new LambdaPacket(actionId, tag));
	}

	@FunctionalInterface
	public interface ServerLambda {
		void run(CompoundTag data, ServerPlayer sender);
	}
}