package com.wzz.lobotocraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public interface IMessage {

	void fromBytes(FriendlyByteBuf buf);
	
    void toBytes(FriendlyByteBuf buf);
	
	void run(NetworkEvent.Context ctx);

	default boolean sendToClient() { return false; }

	default boolean sendToServer() { return false; }
}
