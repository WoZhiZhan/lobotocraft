package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/** Records the notice only after the player presses its confirmation button. */
public final class AcknowledgeWelcomeNoticePacket implements IMessage {
    public static final String SEEN_TAG = "lobotocraft_welcome_notice_seen_v2";

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(SEEN_TAG, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
