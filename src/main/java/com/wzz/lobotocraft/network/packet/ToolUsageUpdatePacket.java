package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.ToolUsageProgressScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 工具使用状态更新数据包（服务器 -> 客户端）
 */
public class ToolUsageUpdatePacket implements IMessage {
    private int abnormalityId;
    private int usageTimeSeconds;
    private float currentDamage;
    private float damageInterval;
    private int totalEnergyProduced;
    private int currentEnergyOutput;
    private float energyInterval;
    private float playerHealth;
    private float playerMaxHealth;

    public ToolUsageUpdatePacket(int abnormalityId, int usageTimeSeconds,
                                 float currentDamage, float damageInterval,
                                 int totalEnergyProduced, int currentEnergyOutput, float energyInterval,
                                 float playerHealth, float playerMaxHealth) {
        this.abnormalityId = abnormalityId;
        this.usageTimeSeconds = usageTimeSeconds;
        this.currentDamage = currentDamage;
        this.damageInterval = damageInterval;
        this.totalEnergyProduced = totalEnergyProduced;
        this.currentEnergyOutput = currentEnergyOutput;
        this.energyInterval = energyInterval;
        this.playerHealth = playerHealth;
        this.playerMaxHealth = playerMaxHealth;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.usageTimeSeconds = buf.readInt();
        this.currentDamage = buf.readFloat();
        this.damageInterval = buf.readFloat();
        this.totalEnergyProduced = buf.readInt();
        this.currentEnergyOutput = buf.readInt();
        this.energyInterval = buf.readFloat();
        this.playerHealth = buf.readFloat();
        this.playerMaxHealth = buf.readFloat();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(usageTimeSeconds);
        buf.writeFloat(currentDamage);
        buf.writeFloat(damageInterval);
        buf.writeInt(totalEnergyProduced);
        buf.writeInt(currentEnergyOutput);
        buf.writeFloat(energyInterval);
        buf.writeFloat(playerHealth);
        buf.writeFloat(playerMaxHealth);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        // 如果当前界面是ToolUsageProgressScreen，更新状态
        if (screen instanceof ToolUsageProgressScreen progressScreen) {
            progressScreen.onStatusUpdate(
                    usageTimeSeconds, currentDamage, damageInterval,
                    totalEnergyProduced, currentEnergyOutput, energyInterval,
                    playerHealth, playerMaxHealth
            );
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}