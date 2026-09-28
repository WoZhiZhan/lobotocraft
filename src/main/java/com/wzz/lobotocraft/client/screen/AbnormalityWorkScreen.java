package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig;
import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.StartWorkPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 异想体工作选择界面
 */
public class AbnormalityWorkScreen extends Screen {
    private static final ResourceLocation INSTINCT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_instinct.png");
    private static final ResourceLocation INSIGHT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_insight.png");
    private static final ResourceLocation ATTACHMENT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_attachment.png");
    private static final ResourceLocation REPRESSION_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_repression.png");
    private static final ResourceLocation MALKUTH_UNKNOWN_ICON =
            ResourceUtil.createInstance("textures/gui/core_suppression/malkuth_unknown_work.png");

    private final int abnormalityId;
    private final float[][] fullWorkPreferences;
    private final RiskLevel riskLevel;
    private final int observationLevel; // 观察等级
    private final int employeeLevel;
    private final boolean[] workPrefsUnlocked;
    private final int repeatedWorkPenaltyPercent;

    private int selectedWork = 0; // 0=本能, 1=洞察, 2=沟通, 3=压迫

    public AbnormalityWorkScreen(int abnormalityId, float[][] fullWorkPreferences, RiskLevel riskLevel, int observationLevel, int employeeLevel, boolean[] workPrefsUnlocked) {
        this(abnormalityId, fullWorkPreferences, riskLevel, observationLevel, employeeLevel,
                workPrefsUnlocked, 0);
    }

    public AbnormalityWorkScreen(int abnormalityId, float[][] fullWorkPreferences, RiskLevel riskLevel,
                                 int observationLevel, int employeeLevel, boolean[] workPrefsUnlocked,
                                 int repeatedWorkPenaltyPercent) {
        super(Component.translatable("screen.lobotocraft.work.title"));
        this.minecraft = Minecraft.getInstance();
        this.abnormalityId = abnormalityId;
        this.fullWorkPreferences = fullWorkPreferences;
        this.riskLevel = riskLevel;
        this.observationLevel = observationLevel;
        this.employeeLevel = employeeLevel;
        this.workPrefsUnlocked = workPrefsUnlocked;
        this.repeatedWorkPenaltyPercent = Math.max(0, repeatedWorkPenaltyPercent);
    }

    // ==================== 界面自适应缩放(只缩不放,不改变正常分辨率下的显示样式) ====================
    private static final int MIN_UI_WIDTH = 340;
    private static final int MIN_UI_HEIGHT = 240;
    private float uiScale = 1f;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        uiScale = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.fitScale(this.width, this.height, MIN_UI_WIDTH, MIN_UI_HEIGHT);
        if (uiScale < 1f) {
            // 缩放前先铺满全屏暗化,避免缩小后四周露出游戏画面
            this.renderBackground(graphics);
        }
        int mx = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
        int my = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
        com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.begin(graphics, this.width, this.height, uiScale);
        try {
            renderContent(graphics, mx, my, partialTick);
        } finally {
            com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.end(graphics, uiScale);
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 渲染标题
        graphics.drawCenteredString(this.font, Component.translatable("screen.lobotocraft.work.title"),
                centerX, centerY - 100, 0xFFFFFF);
        renderRiskLevel(graphics, centerX, centerY - 80);
        // 渲染4个工作图标（田字形布局）
        renderWorkIcon(graphics, centerX - 60, centerY - 30, 0, INSTINCT_ICON,
                Component.translatable("work.lobotocraft.instinct"));
        renderWorkIcon(graphics, centerX + 20, centerY - 30, 1, INSIGHT_ICON,
                Component.translatable("work.lobotocraft.insight"));
        renderWorkIcon(graphics, centerX - 60, centerY + 40, 2, ATTACHMENT_ICON,
                Component.translatable("work.lobotocraft.attachment"));
        renderWorkIcon(graphics, centerX + 20, centerY + 40, 3, REPRESSION_ICON,
                Component.translatable("work.lobotocraft.repression"));

        // 渲染提示
        graphics.drawCenteredString(this.font, Component.translatable("screen.lobotocraft.work.controls"),
                centerX, centerY + 120, 0xFFFF00);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRiskLevel(GuiGraphics graphics, int centerX, int y) {
        if (riskLevel == null) return;

        // 获取风险等级的颜色和名称
        int color = riskLevel.getColor();
        Component name = getRiskLevelName(riskLevel);

        // 渲染风险等级背景框
        int textWidth = this.font.width(name);
        int padding = 10;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = 20;
        int boxX = centerX - boxWidth / 2;
        int boxY = y;

        // 绘制半透明背景
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight,
                (color & 0x00FFFFFF) | 0x88000000); // 80%透明度

        // 绘制边框
        graphics.renderOutline(boxX, boxY, boxWidth, boxHeight, color);

        // 渲染风险等级名称（带颜色）
        int textX = centerX - textWidth / 2;
        int textY = boxY + (boxHeight - 9) / 2; // 居中
        graphics.drawString(this.font, name, textX, textY, color, false);
    }

    private Component getRiskLevelName(RiskLevel level) {
        return Component.translatable("risk.lobotocraft.level", level.name());
    }

    private void renderWorkIcon(GuiGraphics graphics, int x, int y, int workIndex,
                                ResourceLocation icon, Component name) {
        var action = uniqueAction(workIndex);
        if (action != null) {
            com.wzz.lobotocraft.client.ui.LobotomyUiRenderer.drawButton(graphics, font,
                    x, y, 40, 40, Component.literal(action.symbol),
                    selectedWork == workIndex
                            ? com.wzz.lobotocraft.client.ui.LobotomyUiRenderer.ButtonState.SELECTED
                            : com.wzz.lobotocraft.client.ui.LobotomyUiRenderer.ButtonState.DANGER);
            graphics.drawCenteredString(font, Component.translatable(action.labelKey),
                    x + 20, y + 45, com.wzz.lobotocraft.client.ui.LobotomyUiTheme.ALERT_RED);
            return;
        }
        boolean isSelected = (workIndex == selectedWork);
        boolean malkuthScramble = CoreSuppressionClientState.isActive(CoreSuppressionType.MALKUTH);

        if (isSelected) {
            graphics.fill(x - 2, y - 2, x + 42, y + 42, 0xFFFFFF00);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (malkuthScramble) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(40.0F / 136.0F, 40.0F / 184.0F, 1.0F);
            graphics.blit(MALKUTH_UNKNOWN_ICON, 0, 0, 0, 0, 136, 184, 136, 184);
            graphics.pose().popPose();
        } else {
            graphics.blit(icon, x, y, 0, 0, 40, 40, 40, 40);
        }

        Component visibleName = malkuthScramble ? Component.literal("???") : name;
        int nameX = x + 20 - this.font.width(visibleName) / 2;
        graphics.drawString(this.font, visibleName, nameX, y + 45, 0xFFFFFF);

        String rateText;
        int rateColor;

        if (!malkuthScramble && workPrefsUnlocked != null && workIndex < workPrefsUnlocked.length
                && workPrefsUnlocked[workIndex]) {
            // 已解锁，显示真实成功率
            int levelIndex = Math.min(Math.max(employeeLevel - 1, 0), 4);
            if (fullWorkPreferences != null && workIndex < fullWorkPreferences.length
                    && levelIndex < fullWorkPreferences[workIndex].length) {
                float successRate = fullWorkPreferences[workIndex][levelIndex];
                rateText = String.format("%.0f%%", successRate * 100);
                rateColor = EncyclopediaGUIConfig.Colors.getWorkRateColor(successRate);
            } else {
                rateText = "50%";
                rateColor = 0xFFFF00;
            }
        } else {
            rateText = "???";
            rateColor = 0x888888;
        }

        boolean penaltyVisible = !malkuthScramble
                && workPrefsUnlocked != null
                && workIndex < workPrefsUnlocked.length
                && workPrefsUnlocked[workIndex];
        String penaltyText = penaltyVisible && repeatedWorkPenaltyPercent > 0
                ? "(-" + repeatedWorkPenaltyPercent + "%)" : "";
        int totalWidth = this.font.width(rateText)
                + (penaltyText.isEmpty() ? 0 : 3 + this.font.width(penaltyText));
        int rateX = x + 20 - totalWidth / 2;
        graphics.drawString(this.font, rateText, rateX, y + 57, rateColor);
        if (!penaltyText.isEmpty()) {
            graphics.drawString(this.font, penaltyText,
                    rateX + this.font.width(rateText) + 3, y + 57, 0xFFFF5555);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W -> {
                if (selectedWork >= 2) selectedWork -= 2;
                yield true;
            }
            case GLFW.GLFW_KEY_S -> {
                if (selectedWork < 2) selectedWork += 2;
                yield true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (selectedWork % 2 == 1) selectedWork -= 1;
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                if (selectedWork % 2 == 0 && selectedWork < 3) selectedWork += 1;
                yield true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                startWork();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                this.onClose();
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    private void startWork() {
        WorkType workType = WorkType.values()[selectedWork];

        // 进度界面由服务端确认开工后打开，避免开工被拒绝时客户端进入假工作界面。
        MessageLoader.getLoader().sendToServer(
                new StartWorkPacket(abnormalityId, workType)
        );
        this.minecraft.setScreen(null);
    }

    private com.wzz.lobotocraft.work.AbnormalityCommandPolicy.Action uniqueAction(int slot) {
        return minecraft.level != null && minecraft.level.getEntity(abnormalityId)
                instanceof com.wzz.lobotocraft.entity.base.AbstractAbnormality source
                ? com.wzz.lobotocraft.work.AbnormalityCommandPolicy.action(source.getAbnormalityCode(), slot)
                : null;
    }

    public void refreshSharedKnowledge(com.wzz.lobotocraft.capability.PlayerAbnormalityData knowledge) {
        if (minecraft.level == null || !(minecraft.level.getEntity(abnormalityId)
                instanceof com.wzz.lobotocraft.entity.base.AbstractAbnormality source)) return;
        for (int i = 0; i < workPrefsUnlocked.length; i++) {
            workPrefsUnlocked[i] = knowledge.isManualUnlocked(source.getAbnormalityCode(), 100 + i);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 自适应缩放:鼠标坐标逆变换(与渲染变换保持一致,避免点击错位) ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale),
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale),
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale),
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale),
                button, dragX / Math.max(uiScale, 0.0001f), dragY / Math.max(uiScale, 0.0001f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale),
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale), delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale),
                com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale));
    }
}
