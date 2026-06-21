package com.wzz.lobotocraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public interface IMessage {

	void fromBytes(FriendlyByteBuf buf);
	
    void toBytes(FriendlyByteBuf buf);
	
	default void run(NetworkEvent.Context ctx) {}

	// 客户端逻辑扔这
	@OnlyIn(Dist.CLIENT)
	default void runClient(NetworkEvent.Context ctx) { run(ctx); }

	default boolean sendToClient() { return false; }

	default boolean sendToServer() { return false; }
}
