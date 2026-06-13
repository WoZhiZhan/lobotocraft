package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.StartUsingToolPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 工具使用界面 - 针对持续使用型工具异想体
 */
public class ToolUseScreen extends Screen {

    private static final ResourceLocation WARNING_ICON =
            ResourceUtil.createInstance("textures/gui/warning.png");

    private final int abnormalityId;
    private final String abnormalityName;
    private final String toolType; // "continuous" 或其他
    private final String warningTitle;
    private final String[] warningMessages;

    private boolean confirmed = false;
    private int warningTimer = 0;
    private static final int WARNING_BLINK_INTERVAL = 10;

    public ToolUseScreen(int abnormalityId, String abnormalityName, String toolType, String warningTitle, String[] warningMessages) {
        super(Component.literal("使用工具"));
        this.minecraft = Minecraft.getInstance();
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
        this.toolType = toolType;
        this.warningTitle = warningTitle;
        this.warningMessages = warningMessages;
    }

    @Override
    public void tick() {
        super.tick();
        warningTimer++;
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

        // 面板背景
        int panelWidth = 400;
        int panelHeight = 300;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        graphics.fill(panelX - 2, panelY - 2,
                panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFFFF0000);
        graphics.fill(panelX, panelY,
                panelX + panelWidth, panelY + panelHeight, 0xFF000000);

        // 标题
        graphics.drawCenteredString(this.font, "§l工具使用确认",
                centerX, panelY + 20, 0xFFFF00);

        // 异想体名称
        graphics.drawCenteredString(this.font, "§e" + abnormalityName,
                centerX, panelY + 45, 0xFFFFFF);

        // 警告图标（闪烁）
        if ((warningTimer / WARNING_BLINK_INTERVAL) % 2 == 0) {
            RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F);
            graphics.blit(WARNING_ICON, centerX - 16, panelY + 70,
                    0, 0, 32, 32, 32, 32);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // 警告信息
        int textY = panelY + 115;
        graphics.drawCenteredString(this.font, warningTitle,
                centerX, textY, 0xFF0000);

        textY += 20;
        // 显示动态警告信息
        for (String message : warningMessages) {
            graphics.drawCenteredString(this.font, message,
                    centerX, textY, 0xFFFFFF);
            textY += 15;
        }

        textY += 10;
        graphics.drawCenteredString(this.font, "§e确定要继续吗？",
                centerX, textY, 0xFFFF00);

        // 按钮提示
        textY += 30;
        if (!confirmed) {
            graphics.drawCenteredString(this.font, "§a按下 Y 确认使用",
                    centerX, textY, 0x00FF00);
            textY += 15;
            graphics.drawCenteredString(this.font, "§7按下 ESC 取消",
                    centerX, textY, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(this.font, "§6员工正在进入...",
                    centerX, textY, 0xFFAA00);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmed) return true;

        return switch (keyCode) {
            case GLFW.GLFW_KEY_Y -> {
                confirmUse();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                this.onClose();
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    private void confirmUse() {
        confirmed = true;

        MessageLoader.getLoader().sendToServer(
                new StartUsingToolPacket(abnormalityId, abnormalityName)
        );

        this.onClose();
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
