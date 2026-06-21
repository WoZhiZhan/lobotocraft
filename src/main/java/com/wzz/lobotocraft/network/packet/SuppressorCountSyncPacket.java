package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 抑制计数同步包:服务端 -> 客户端,用于左上角 HUD 显示。
 * 将计数写入客户端玩家持久数据,供 SuppressorCounterHud 读取。
 */
public class SuppressorCountSyncPacket implements IMessage {

    private int count;

    public SuppressorCountSyncPacket(int count) {
        this.count = count;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.count = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(count);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            CompoundTag root = player.getPersistentData();
            CompoundTag persist = root.getCompound(Player.PERSISTED_NBT_TAG);
            persist.putBoolean("lobotocraft_suppressor_init", true);
            persist.putInt("lobotocraft_suppressor_count", count);
            root.put(Player.PERSISTED_NBT_TAG, persist);
        }
    }
}
