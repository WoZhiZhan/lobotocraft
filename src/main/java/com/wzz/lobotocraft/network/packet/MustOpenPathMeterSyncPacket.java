package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class MustOpenPathMeterSyncPacket implements IMessage {
    public static final String CLIENT_METER_TAG = "lobotocraft_must_open_path_meter";
    private int meter;

    public MustOpenPathMeterSyncPacket() {
    }

    public MustOpenPathMeterSyncPacket(int meter) {
        this.meter = Math.max(0, Math.min(100, meter));
    }

    @Override public boolean sendToClient() { return true; }
    @Override public void fromBytes(FriendlyByteBuf buf) { meter = buf.readUnsignedByte(); }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeByte(meter); }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putInt(CLIENT_METER_TAG, meter);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
