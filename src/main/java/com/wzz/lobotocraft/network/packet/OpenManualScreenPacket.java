package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.AbnormalityEncyclopediaScreen;
import com.wzz.lobotocraft.client.screen.DepartmentAssignmentScreen;
import com.wzz.lobotocraft.client.screen.EquipmentCraftingScreen;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 打开异想体图鉴界面数据包（服务器 -> 客户端）
 * 发送完整的异想体信息
 */
public class OpenManualScreenPacket implements IMessage {
    private static final int MAX_MANUALS = 256;
    private int abnormalityId;
    private String abnormalityCode;      // 编号 如 O-03-03
    private String abnormalityName;      // 名称
    private RiskLevel riskLevel;            // 风险等级
    private String damageType;           // 伤害类型 WHITE/RED/BLACK/PALE
    private int maxPEOutput;             // 最大PE-BOX产量
    private int developmentWeaponCount;
    private int developmentArmorCount;
    private boolean isToolType;

    private float[][] workPreferences;   // [4种工作][5个等级] 的成功率
    private int observationLevel;        // 当前观察等级

    // 已解锁的信息（通过位标记）
    private boolean basicInfoUnlocked;
    private boolean[] workPreferencesUnlocked;
    private boolean sensitiveInfoUnlocked;
    private boolean[] manualsUnlocked;

    // 解锁成本
    private int basicInfoCost;
    private int workPreferencesCost;
    private int sensitiveInfoCost;
    private int manualCost;  // 所有管理须知使用相同成本
    private double scrollOffset;

    public OpenManualScreenPacket(int abnormalityId, String code, String name,
                                  RiskLevel riskLevel, String damageType, int maxPEOutput,
                                  float[][] workPreferences, int observationLevel,
                                  boolean basicInfo, boolean[] workPref, boolean sensitiveInfo,
                                  boolean[] manualsUnlocked,
                                  int basicInfoCost, int workPreferencesCost,
                                  int sensitiveInfoCost, int manualCost, int developmentWeaponCount, int developmentArmorCount, double scrollOffset, boolean isToolType) {
        this.abnormalityId = abnormalityId;
        this.abnormalityCode = code;
        this.abnormalityName = name;
        this.riskLevel = riskLevel;
        this.damageType = damageType;
        this.maxPEOutput = maxPEOutput;
        this.workPreferences = workPreferences;
        this.observationLevel = observationLevel;
        this.basicInfoUnlocked = basicInfo;
        this.workPreferencesUnlocked = workPref;
        this.sensitiveInfoUnlocked = sensitiveInfo;
        this.manualsUnlocked = manualsUnlocked;
        this.basicInfoCost = basicInfoCost;
        this.workPreferencesCost = workPreferencesCost;
        this.sensitiveInfoCost = sensitiveInfoCost;
        this.manualCost = manualCost;
        this.developmentWeaponCount = developmentWeaponCount;
        this.developmentArmorCount = developmentArmorCount;
        this.scrollOffset = scrollOffset;
        this.isToolType = isToolType;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.abnormalityCode = buf.readUtf();
        this.abnormalityName = buf.readUtf();
        this.riskLevel = buf.readEnum(RiskLevel.class);
        this.damageType = buf.readUtf();
        this.maxPEOutput = buf.readInt();

        // 读取工作偏好矩阵
        this.workPreferences = new float[4][5];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                workPreferences[i][j] = buf.readFloat();
            }
        }

        this.observationLevel = buf.readInt();
        this.basicInfoUnlocked = buf.readBoolean();
        this.workPreferencesUnlocked = new boolean[4];
        for (int i = 0; i < 4; i++) {
            workPreferencesUnlocked[i] = buf.readBoolean();
        }
        this.sensitiveInfoUnlocked = buf.readBoolean();
        int manualCount = PacketDecodeLimits.readIntCount(buf, MAX_MANUALS, "manual unlock");
        this.manualsUnlocked = new boolean[manualCount];
        for (int i = 0; i < manualCount; i++) {
            manualsUnlocked[i] = buf.readBoolean();
        }

        // 读取解锁成本
        this.basicInfoCost = buf.readInt();
        this.workPreferencesCost = buf.readInt();
        this.sensitiveInfoCost = buf.readInt();
        this.manualCost = buf.readInt();
        this.developmentWeaponCount = buf.readInt();
        this.developmentArmorCount = buf.readInt();
        this.scrollOffset = buf.readDouble();
        this.isToolType = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeUtf(abnormalityCode);
        buf.writeUtf(abnormalityName);
        buf.writeEnum(riskLevel);
        buf.writeUtf(damageType);
        buf.writeInt(maxPEOutput);

        // 写入工作偏好矩阵
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                float value = workPreferences != null
                        && i < workPreferences.length
                        && workPreferences[i] != null
                        && j < workPreferences[i].length
                        ? workPreferences[i][j] : 0.5F;
                buf.writeFloat(value);
            }
        }

        buf.writeInt(observationLevel);
        buf.writeBoolean(basicInfoUnlocked);
        for (int i = 0; i < 4; i++) {
            buf.writeBoolean(workPreferencesUnlocked != null
                    && i < workPreferencesUnlocked.length && workPreferencesUnlocked[i]);
        }
        buf.writeBoolean(sensitiveInfoUnlocked);
        int manualCount = Math.min(manualsUnlocked.length, MAX_MANUALS);
        buf.writeInt(manualCount);
        for (int i = 0; i < manualCount; i++) {
            buf.writeBoolean(manualsUnlocked[i]);
        }

        // 写入解锁成本
        buf.writeInt(basicInfoCost);
        buf.writeInt(workPreferencesCost);
        buf.writeInt(sensitiveInfoCost);
        buf.writeInt(manualCost);
        buf.writeInt(developmentWeaponCount);
        buf.writeInt(developmentArmorCount);
        buf.writeDouble(scrollOffset);
        buf.writeBoolean(isToolType);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen returnScreen = null;
        if (minecraft.screen instanceof com.wzz.lobotocraft.client.screen.RoomMapScreen
                || minecraft.screen instanceof AbnormalityEncyclopediaScreen) {
            returnScreen = minecraft.screen instanceof AbnormalityEncyclopediaScreen encyclopedia
                    ? encyclopedia.getReturnScreen() : minecraft.screen;
        } else if (minecraft.screen instanceof EquipmentCraftingScreen crafting) {
            returnScreen = crafting.getReturnScreen();
        } else if (minecraft.screen instanceof DepartmentAssignmentScreen) {
            returnScreen = minecraft.screen;
        }
        AbnormalityEncyclopediaData.EntryData data =
                AbnormalityEncyclopediaData.getData(abnormalityCode);

        // 从数据中获取管理须知文本
        int manualCount = data.getManualCount();
        String[] manualTexts = new String[manualCount];
        for (int i = 0; i < manualCount; i++) {
            manualTexts[i] = data.getManual(i);
        }

        Minecraft.getInstance().setScreen(new AbnormalityEncyclopediaScreen(
                abnormalityId, abnormalityCode, abnormalityName, riskLevel,
                damageType, maxPEOutput, workPreferences, observationLevel,
                manualTexts,
                AbnormalityEncyclopediaData.getStories(abnormalityCode),
                data.sensitiveInfo(), returnScreen,
                basicInfoUnlocked, workPreferencesUnlocked, sensitiveInfoUnlocked, manualsUnlocked,
                basicInfoCost, workPreferencesCost, sensitiveInfoCost, manualCost, developmentWeaponCount, developmentArmorCount, scrollOffset, isToolType
        ));
    }
}
