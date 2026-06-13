package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.StopUsingToolPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 工具使用进度界面 - 显示持续使用型工具的实时状态
 */
public class ToolUsageProgressScreen extends Screen {

    private static final ResourceLocation ENERGY_ICON =
            ResourceUtil.createInstance("textures/gui/positive_energy.png");
    private static final ResourceLocation DAMAGE_ICON =
            ResourceUtil.createInstance("textures/gui/negative_energy.png");

    private final int abnormalityId;
    private final String abnormalityName;

    // 状态数据（由服务器更新）
    private int usageTimeSeconds = 0;
    private float currentDamage = 0f;
    private float damageInterval = 5f;
    private int totalEnergyProduced = 0;
    private int currentEnergyOutput = 1;
    private float energyInterval = 5f;

    // 血量显示
    private float playerHealth = 20f;
    private float playerMaxHealth = 20f;

    // 动画计时器
    private int animationTimer = 0;

    // 取消按钮
    private Button cancelButton = null;

    public ToolUsageProgressScreen(int abnormalityId, String abnormalityName) {
        super(Component.literal("工具使用中"));
        this.abnormalityId = abnormalityId;
        this.abnormalityName = abnormalityName;
    }

    /**
     * 接收服务器发送的状态更新
     */
    public void onStatusUpdate(int usageTimeSeconds, float currentDamage, float damageInterval,
                               int totalEnergyProduced, int currentEnergyOutput, float energyInterval,
                               float playerHealth, float playerMaxHealth) {
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
    public void init() {
        super.init();

        // 创建取消按钮（居中下方），一直显示
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int buttonY = this.height - 40;

        cancelButton = Button.builder(
                Component.literal("§c取消使用"),
                button -> onCancelButtonPressed()
        ).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(cancelButton);
    }

    /**
     * 取消按钮被按下
     */
    private void onCancelButtonPressed() {
        // 发送停止使用请求到服务器
        MessageLoader.getLoader().sendToServer(new StopUsingToolPacket(abnormalityId));

        // 关闭界面
        this.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        animationTimer++;

        // 如果血量为0，自动关闭界面
        if (playerHealth <= 0) {
            this.onClose();
        }
    }

    // ==================== 界面自适应缩放(只缩不放,不改变正常分辨率下的显示样式) ====================
    private static final int MIN_UI_WIDTH = 340;
    private static final int MIN_UI_HEIGHT = 220;
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
        // 半透明背景
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 主面板
        renderMainPanel(graphics, centerX, centerY);

        // 状态栏
        renderStatusBar(graphics, centerX, 20);

        // 警告提示
        renderWarning(graphics, centerX, this.height - 70);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainPanel(GuiGraphics graphics, int centerX, int centerY) {
        int panelWidth = 500;
        int panelHeight = 350;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        // 边框（红色闪烁）
        int borderColor = (animationTimer / 10) % 2 == 0 ? 0xFFFF0000 : 0xFFAA0000;
        graphics.fill(panelX - 3, panelY - 3,
                panelX + panelWidth + 3, panelY + panelHeight + 3, borderColor);
        graphics.fill(panelX, panelY,
                panelX + panelWidth, panelY + panelHeight, 0xFF1A1A1A);

        // 标题
        graphics.drawCenteredString(this.font, "§c§l" + abnormalityName,
                centerX, panelY + 15, 0xFF0000);

        // 使用时间
        int contentY = panelY + 45;
        graphics.drawCenteredString(this.font,
                String.format("§e使用时间：%d秒", usageTimeSeconds),
                centerX, contentY, 0xFFFF00);

        // 分隔线
        contentY += 25;
        graphics.fill(panelX + 30, contentY, panelX + panelWidth - 30, contentY + 2, 0xFF666666);

        // 伤害信息
        contentY += 20;
        graphics.drawString(this.font, "§c当前伤害：",
                panelX + 40, contentY, 0xFF0000);
        graphics.drawString(this.font, String.format("§f%.1f", currentDamage),
                panelX + 180, contentY, 0xFFFFFF);

        contentY += 20;
        graphics.drawString(this.font, "§c伤害间隔：",
                panelX + 40, contentY, 0xFF0000);
        graphics.drawString(this.font, String.format("§f%.1f秒", damageInterval),
                panelX + 180, contentY, 0xFFFFFF);

        // 伤害图标
        RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F);
        graphics.blit(DAMAGE_ICON, panelX + panelWidth - 80, contentY - 30,
                0, 0, 48, 48, 48, 48);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 分隔线
        contentY += 30;
        graphics.fill(panelX + 30, contentY, panelX + panelWidth - 30, contentY + 2, 0xFF666666);

        // 能源信息
        contentY += 20;
        graphics.drawString(this.font, "§6总能源产出：",
                panelX + 40, contentY, 0xFFAA00);
        graphics.drawString(this.font, String.format("§f%d", totalEnergyProduced),
                panelX + 180, contentY, 0xFFFFFF);

        contentY += 20;
        graphics.drawString(this.font, "§6当前产能：",
                panelX + 40, contentY, 0xFFAA00);
        graphics.drawString(this.font, String.format("§f%d/次", currentEnergyOutput),
                panelX + 180, contentY, 0xFFFFFF);

        contentY += 20;
        graphics.drawString(this.font, "§6产能间隔：",
                panelX + 40, contentY, 0xFFAA00);
        graphics.drawString(this.font, String.format("§f%.1f秒", energyInterval),
                panelX + 180, contentY, 0xFFFFFF);

        // 能源图标
        RenderSystem.setShaderColor(1.0F, 1.0F, 0.0F, 1.0F);
        graphics.blit(ENERGY_ICON, panelX + panelWidth - 80, contentY - 30,
                0, 0, 48, 48, 48, 48);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 分隔线
        contentY += 30;
        graphics.fill(panelX + 30, contentY, panelX + panelWidth - 30, contentY + 2, 0xFF666666);

        // 血量条
        contentY += 20;
        renderHealthBar(graphics, panelX + 40, contentY, panelWidth - 80);
    }

    private void renderHealthBar(GuiGraphics graphics, int x, int y, int width) {
        // 背景
        graphics.fill(x, y, x + width, y + 30, 0xFF333333);

        // 血量填充
        float healthPercent = playerHealth / playerMaxHealth;
        int fillWidth = (int)(width * healthPercent);

        // 颜色根据血量变化
        int fillColor;
        if (healthPercent > 0.6f) {
            fillColor = 0xFF00FF00; // 绿色
        } else if (healthPercent > 0.3f) {
            fillColor = 0xFFFFAA00; // 橙色
        } else {
            fillColor = 0xFFFF0000; // 红色
        }

        graphics.fill(x + 2, y + 2, x + fillWidth - 2, y + 28, fillColor);

        // 文字
        String healthText = String.format("§f生命值: %.1f / %.1f", playerHealth, playerMaxHealth);
        graphics.drawCenteredString(this.font, healthText,
                x + width / 2, y + 10, 0xFFFFFF);
    }

    private void renderStatusBar(GuiGraphics graphics, int centerX, int y) {
        String status = "§c§l使用中";
        graphics.drawCenteredString(this.font, status, centerX, y, 0xFF0000);
    }

    private void renderWarning(GuiGraphics graphics, int centerX, int y) {
        // 闪烁警告
        if ((animationTimer / 15) % 2 == 0) {
            graphics.drawCenteredString(this.font,
                    "§4§l警告：员工将持续受到伤害直到死亡！",
                    centerX, y, 0xFF0000);
        }

        graphics.drawCenteredString(this.font,
                "§7点击下方按钮可尝试取消使用",
                centerX, y + 15, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // 允许ESC关闭并发送取消请求
        return true;
    }

    @Override
    public void onClose() {
        // ESC关闭时也发送取消请求
        if (this.minecraft != null && this.minecraft.player != null) {
            MessageLoader.getLoader().sendToServer(new StopUsingToolPacket(abnormalityId));
        }
        super.onClose();
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
