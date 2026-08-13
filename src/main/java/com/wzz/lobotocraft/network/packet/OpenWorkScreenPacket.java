package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.AbnormalityWorkScreen;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenWorkScreenPacket implements IMessage {
    private static final int WORK_ROWS = 4;
    private static final int WORK_COLUMNS = 5;
    private int abnormalityId;
    private float[][] fullWorkPreferences;
    private RiskLevel riskLevel;
    private int observationLevel;
    private int employeeLevel;
    private boolean[] workPrefsUnlocked;
    private int repeatedWorkPenaltyPercent;

    public OpenWorkScreenPacket(int abnormalityId, float[][] fullWorkPreferences, RiskLevel riskLevel,
                                int observationLevel, int employeeLevel, boolean[] workPrefsUnlocked) {
        this(abnormalityId, fullWorkPreferences, riskLevel, observationLevel, employeeLevel,
                workPrefsUnlocked, 0);
    }

    public OpenWorkScreenPacket(int abnormalityId, float[][] fullWorkPreferences, RiskLevel riskLevel,
                                int observationLevel, int employeeLevel, boolean[] workPrefsUnlocked,
                                int repeatedWorkPenaltyPercent) {
        this.abnormalityId = abnormalityId;
        this.fullWorkPreferences = fullWorkPreferences;
        this.riskLevel = riskLevel;
        this.observationLevel = observationLevel;
        this.employeeLevel = employeeLevel;
        this.workPrefsUnlocked = workPrefsUnlocked;
        this.repeatedWorkPenaltyPercent = Math.max(0, repeatedWorkPenaltyPercent);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        int rows = buf.readInt(); // 工作类型数量 (4)
        int cols = buf.readInt(); // 观察等级数量 (5)
        PacketDecodeLimits.requireExact(rows, WORK_ROWS, "work preference rows");
        PacketDecodeLimits.requireExact(cols, WORK_COLUMNS, "work preference columns");
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
        PacketDecodeLimits.requireExact(unlockedCount, WORK_ROWS, "work preference unlocks");
        this.workPrefsUnlocked = new boolean[unlockedCount];
        for (int i = 0; i < unlockedCount; i++) {
            this.workPrefsUnlocked[i] = buf.readBoolean();
        }
        this.repeatedWorkPenaltyPercent = Math.max(0, buf.readVarInt());
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(WORK_ROWS);
        buf.writeInt(WORK_COLUMNS);
        for (int i = 0; i < WORK_ROWS; i++) {
            for (int j = 0; j < WORK_COLUMNS; j++) {
                float value = fullWorkPreferences != null
                        && i < fullWorkPreferences.length
                        && fullWorkPreferences[i] != null
                        && j < fullWorkPreferences[i].length
                        ? fullWorkPreferences[i][j] : 0.5F;
                buf.writeFloat(value);
            }
        }
        buf.writeEnum(riskLevel);
        buf.writeInt(observationLevel);
        buf.writeInt(employeeLevel);
        buf.writeInt(WORK_ROWS);
        for (int i = 0; i < WORK_ROWS; i++) {
            buf.writeBoolean(workPrefsUnlocked != null
                    && i < workPrefsUnlocked.length && workPrefsUnlocked[i]);
        }
        buf.writeVarInt(Math.max(0, repeatedWorkPenaltyPercent));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().setScreen(new AbnormalityWorkScreen(
                abnormalityId, fullWorkPreferences, riskLevel, observationLevel, employeeLevel,
                workPrefsUnlocked, repeatedWorkPenaltyPercent
        ));
    }

    @Override
    public boolean sendToClient() { return true; }
}
