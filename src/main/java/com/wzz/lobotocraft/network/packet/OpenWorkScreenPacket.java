package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.AbnormalityWorkScreen;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenWorkScreenPacket implements IMessage {
    private int abnormalityId;
    private float[][] fullWorkPreferences;
    private RiskLevel riskLevel;
    private int observationLevel;
    private int employeeLevel;
    private boolean[] workPrefsUnlocked;

    public OpenWorkScreenPacket(int abnormalityId, float[][] fullWorkPreferences, RiskLevel riskLevel,
                                int observationLevel, int employeeLevel, boolean[] workPrefsUnlocked) {
        this.abnormalityId = abnormalityId;
        this.fullWorkPreferences = fullWorkPreferences;
        this.riskLevel = riskLevel;
        this.observationLevel = observationLevel;
        this.employeeLevel = employeeLevel;
        this.workPrefsUnlocked = workPrefsUnlocked;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        int rows = buf.readInt(); // 工作类型数量 (4)
        int cols = buf.readInt(); // 观察等级数量 (5)
        float[][] prefs = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                prefs[i][j] = buf.readFloat();
            }
        }
        this.fullWorkPreferences = prefs;
        this.riskLevel = buf.readEnum(RiskLevel.class);
        this.observationLevel = buf.readInt();
        this.employeeLevel = buf.readInt();
        int unlockedCount = buf.readInt();
        this.workPrefsUnlocked = new boolean[unlockedCount];
        for (int i = 0; i < unlockedCount; i++) {
            this.workPrefsUnlocked[i] = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        if (fullWorkPreferences == null) {
            buf.writeInt(4); // 4种工作
            buf.writeInt(5); // 5个等级
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 5; j++) {
                    buf.writeFloat(0.5f); // 默认成功率
                }
            }
        } else {
            buf.writeInt(fullWorkPreferences.length); // 行数
            buf.writeInt(fullWorkPreferences[0].length); // 列数（假设所有行长度相同）
            for (float[] row : fullWorkPreferences) {
                for (float value : row) {
                    buf.writeFloat(value);
                }
            }
        }
        buf.writeEnum(riskLevel);
        buf.writeInt(observationLevel);
        buf.writeInt(employeeLevel);
        buf.writeInt(workPrefsUnlocked.length);
        for (boolean b : workPrefsUnlocked) {
            buf.writeBoolean(b);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().setScreen(new AbnormalityWorkScreen(
                abnormalityId, fullWorkPreferences, riskLevel, observationLevel, employeeLevel, workPrefsUnlocked
        ));
    }

    @Override
    public boolean sendToClient() { return true; }
}