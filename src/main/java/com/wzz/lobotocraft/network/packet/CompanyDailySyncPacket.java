package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/**
 * 公司日常数据同步包
 * 从服务端同步到客户端，用于HUD显示
 */
public class CompanyDailySyncPacket implements IMessage {
    
    private int currentDay;
    private int todayWorkCount;
    private boolean armorLocked;
    private boolean isHasSleep;

    public CompanyDailySyncPacket(int currentDay, int todayWorkCount, boolean armorLocked, boolean isHasSleep) {
        this.currentDay = currentDay;
        this.todayWorkCount = todayWorkCount;
        this.armorLocked = armorLocked;
        this.isHasSleep = isHasSleep;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.currentDay = buf.readInt();
        this.todayWorkCount = buf.readInt();
        this.armorLocked = buf.readBoolean();
        this.isHasSleep = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(currentDay);
        buf.writeInt(todayWorkCount);
        buf.writeBoolean(armorLocked);
        buf.writeBoolean(isHasSleep);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                    data.setCurrentDay(currentDay);
                    data.setTodayWorkCount(todayWorkCount);
                    data.setHasSleep(isHasSleep);
                    if (armorLocked) {
                        data.lockArmor();
                    } else {
                        data.unlockArmor();
                    }
                });
            }
        });
        ctx.setPacketHandled(true);
    }
}