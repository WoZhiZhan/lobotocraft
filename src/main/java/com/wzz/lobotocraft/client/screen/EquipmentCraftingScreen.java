package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.ego.EgoResearchScope;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.DevelopEquipmentPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 装备制造进度界面
 */
public class EquipmentCraftingScreen extends Screen {
    
    // 制造类型
    private final String craftingType; // "weapon" 或 "armor"
    private final String abnormalityCode;
    private final int abnormalityId;
    private final double scrollOffset;
    private final EgoResearchScope researchScope;
    
    // 装备数据
    private final ResourceLocation equipmentIcon;
    private final String equipmentName;
    private final RiskLevel riskLevel;
    private final int cost;
    private final Screen returnScreen;
    
    // 进度相关
    private static final int CRAFTING_DURATION = 100; // 5秒 (100 ticks)
    private int craftingTicks = 0;
    private boolean isCompleted = false;
    
    // 动画相关
    private float glowAnimation = 0f;
    private float particleAnimation = 0f;
    
    // UI尺寸
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;
    private static final int ICON_SIZE = 80;

    public EquipmentCraftingScreen(String craftingType, String abnormalityCode, 
                                   int abnormalityId, double scrollOffset,
                                   IAbnormality abnormality, int cost) {
        this(craftingType, abnormalityCode, abnormalityId, scrollOffset, abnormality, cost,
                EgoResearchScope.PERSONAL, null);
    }

    public EquipmentCraftingScreen(String craftingType, String abnormalityCode,
                                   int abnormalityId, double scrollOffset,
                                   IAbnormality abnormality, int cost,
                                   EgoResearchScope researchScope) {
        this(craftingType, abnormalityCode, abnormalityId, scrollOffset, abnormality, cost,
                researchScope, null);
    }

    public EquipmentCraftingScreen(String craftingType, String abnormalityCode,
                                   int abnormalityId, double scrollOffset,
                                   IAbnormality abnormality, int cost,
                                   EgoResearchScope researchScope,
                                   Screen returnScreen) {
        super(Component.literal("装备制造中..."));
        this.craftingType = craftingType;
        this.abnormalityCode = abnormalityCode;
        this.abnormalityId = abnormalityId;
        this.scrollOffset = scrollOffset;
        this.cost = cost;
        this.researchScope = researchScope;
        this.returnScreen = returnScreen;
        
        // 根据类型获取装备数据
        if ("weapon".equals(craftingType)) {
            EGOEquipmentData.WeaponData weaponData = abnormality.getEGOWeaponData();
            this.equipmentIcon = weaponData.iconTexture();
            this.equipmentName = weaponData.name();
            this.riskLevel = weaponData.riskLevel();
        } else {
            EGOEquipmentData.ArmorData armorData = abnormality.getEGOArmorData();
            this.equipmentIcon = armorData.iconTexture();
            this.equipmentName = armorData.name();
            this.riskLevel = armorData.riskLevel();
        }
    }

    @Override
    public void init() {
        super.init();
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!isCompleted) {
            craftingTicks++;
            
            // 动画更新
            glowAnimation += 0.1f;
            particleAnimation += 0.15f;
            
            // 制造完成
            if (craftingTicks >= CRAFTING_DURATION) {
                completeCrafting();
            }
        }
    }

    // ==================== 界面自适应缩放(只缩不放,不改变正常分辨率下的显示样式) ====================
    private static final int MIN_UI_WIDTH = 360;
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
        // 半透明黑色背景
        graphics.fill(0, 0, this.width, this.height, LobotomyUiTheme.BG_SCRIM);
        
        // 计算面板位置（居中）
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        
        // 绘制主面板
        renderMainPanel(graphics, panelX, panelY);
        
        // 绘制装备图标
        renderEquipmentIcon(graphics, panelX, panelY, partialTick);
        
        // 绘制装备信息
        renderEquipmentInfo(graphics, panelX, panelY);
        
        // 绘制进度条
        renderProgressBar(graphics, panelX, panelY, partialTick);
        
        // 绘制状态文本
        renderStatusText(graphics, panelX, panelY);

        String scopeText = researchScope == EgoResearchScope.PERSONAL ? "个人授权" : "AI 全局库存";
        LobotomyUiRenderer.drawTag(graphics, this.font,
                panelX + (PANEL_WIDTH - this.font.width(scopeText) - 8) / 2, panelY + 8,
                scopeText,
                researchScope == EgoResearchScope.PERSONAL
                        ? LobotomyUiTheme.GOLD : LobotomyUiTheme.ACCENT);
        
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 绘制主面板背景：外圈青白呼吸光 + 不透底面板
     */
    private void renderMainPanel(GuiGraphics graphics, int x, int y) {
        // 外圈呼吸光边（只画面板四周的 2px 环，不透进面板内部）
        int glowColor = getGlowColor();
        graphics.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y, glowColor);
        graphics.fill(x - 2, y + PANEL_HEIGHT, x + PANEL_WIDTH + 2, y + PANEL_HEIGHT + 2, glowColor);
        graphics.fill(x - 2, y, x, y + PANEL_HEIGHT, glowColor);
        graphics.fill(x + PANEL_WIDTH, y, x + PANEL_WIDTH + 2, y + PANEL_HEIGHT, glowColor);

        // 不透底主底 + 主题面板（描边/受光上缘/角括号）
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, LobotomyUiTheme.BG_PANEL_SOLID);
        LobotomyUiRenderer.drawPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    /**
     * 绘制装备图标
     */
    private void renderEquipmentIcon(GuiGraphics graphics, int x, int y, float partialTick) {
        int iconX = x + (PANEL_WIDTH - ICON_SIZE) / 2;
        int iconY = y + 20;
        
        // 图标底：内嵌盒 + 青白脉动微光 + 角括号
        LobotomyUiRenderer.drawInsetBox(graphics, iconX - 5, iconY - 5,
                ICON_SIZE + 10, ICON_SIZE + 10);
        float pulse = Mth.sin((craftingTicks + partialTick) * 0.1f) * 0.5f + 0.5f;
        graphics.fill(iconX - 4, iconY - 4, iconX + ICON_SIZE + 4, iconY + ICON_SIZE + 4,
                LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ACCENT, 0.06f + 0.10f * pulse));
        LobotomyUiRenderer.drawCorners(graphics, iconX - 5, iconY - 5,
                ICON_SIZE + 10, ICON_SIZE + 10, LobotomyUiTheme.CORNER_LEN,
                LobotomyUiTheme.LINE_ACCENT);
        
        // 绘制装备图标
        if (equipmentIcon != null && ResourceUtil.exists(equipmentIcon)) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(equipmentIcon, iconX, iconY, 0, 0, 
                         ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
        
        // 制造中的粒子效果（四个角的光点）
        if (!isCompleted) {
            renderCraftingParticles(graphics, iconX, iconY, partialTick);
        }
    }

    /**
     * 绘制制造粒子效果
     */
    private void renderCraftingParticles(GuiGraphics graphics, int iconX, int iconY, float partialTick) {
        float angle = (craftingTicks + partialTick) * 0.05f;
        int radius = 50;
        
        for (int i = 0; i < 4; i++) {
            float currentAngle = angle + (i * Mth.PI / 2);
            int px = iconX + ICON_SIZE / 2 + (int)(Mth.cos(currentAngle) * radius);
            int py = iconY + ICON_SIZE / 2 + (int)(Mth.sin(currentAngle) * radius);
            
            // 绘制小光点
            float alpha = (Mth.sin(particleAnimation + i) * 0.5f + 0.5f);
            int color = LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ENERGY_GREEN, alpha * 0.78f);
            graphics.fill(px - 2, py - 2, px + 2, py + 2, color);
        }
    }

    /**
     * 绘制装备信息
     */
    private void renderEquipmentInfo(GuiGraphics graphics, int x, int y) {
        int infoY = y + 110;
        
        // 装备类型
        String typeText = "weapon".equals(craftingType) ? "E.G.O 武器" : "E.G.O 护甲";
        graphics.drawCenteredString(this.font, typeText,
                x + PANEL_WIDTH / 2, infoY, LobotomyUiTheme.TEXT_MUTED);

        // 装备名称
        graphics.drawCenteredString(this.font, equipmentName,
                x + PANEL_WIDTH / 2, infoY + 12, LobotomyUiTheme.TEXT_TITLE);
        
        // 风险等级
        int riskColor = riskLevel.getColor();
        graphics.drawCenteredString(this.font, "[ " + riskLevel + " ]",
                x + PANEL_WIDTH / 2, infoY + 24, riskColor);
    }

    /**
     * 绘制进度条
     */
    private void renderProgressBar(GuiGraphics graphics, int x, int y, float partialTick) {
        int barY = y + 150;
        int barX = x + 30;
        int barWidth = PANEL_WIDTH - 60;
        int barHeight = 16;

        // 计算进度
        float progress = (craftingTicks + partialTick) / CRAFTING_DURATION;
        progress = Mth.clamp(progress, 0f, 1f);

        // 主题进度条（条底/描边/光带由 renderer 内建）
        LobotomyUiRenderer.drawBar(graphics, barX, barY, barWidth, barHeight,
                progress, 1f, LobotomyUiTheme.ACCENT);

        // 闪烁效果
        int filledWidth = (int)(barWidth * progress);
        if (!isCompleted && filledWidth > 0) {
            float shine = Mth.sin(glowAnimation) * 0.25f + 0.25f;
            graphics.fill(barX, barY, barX + filledWidth, barY + 2,
                    LobotomyUiRenderer.withAlpha(0xFFFFFFFF, shine));
        }

        // 进度百分比文字
        String progressText = String.format("%.0f%%", progress * 100);
        graphics.drawCenteredString(this.font, progressText,
                barX + barWidth / 2, barY + 4, LobotomyUiTheme.TEXT_TITLE);
    }

    /**
     * 绘制状态文本
     */
    private void renderStatusText(GuiGraphics graphics, int x, int y) {
        int textY = y + 180;
        
        if (isCompleted) {
            graphics.drawCenteredString(this.font, "制造完成！",
                    x + PANEL_WIDTH / 2, textY, LobotomyUiTheme.ENERGY_GREEN);
        } else {
            int remainingSeconds = (CRAFTING_DURATION - craftingTicks) / 20 + 1;
            String statusText = String.format("制造中... %d秒", remainingSeconds);

            // 呼吸闪烁效果
            float alpha = Mth.sin(glowAnimation * 2) * 0.3f + 0.7f;
            int textColor = LobotomyUiRenderer.withAlpha(LobotomyUiTheme.WARN_YELLOW, alpha);

            graphics.drawCenteredString(this.font, statusText,
                    x + PANEL_WIDTH / 2, textY, textColor);
        }
    }

    /**
     * 完成制造
     */
    private void completeCrafting() {
        isCompleted = true;
        
        // 播放完成音效
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ModSounds.UNLOCK_INFORMATION.get(), 1.0F, 1.2F);
        }
        
        // 发送网络包到服务器
        MessageLoader.getLoader().sendToServer(
            new DevelopEquipmentPacket(abnormalityCode, craftingType, abnormalityId, scrollOffset,
                    researchScope.id())
        );
    }

    /**
     * 获取发光颜色（青白脉动效果）
     */
    private int getGlowColor() {
        float pulse = Mth.sin(glowAnimation) * 0.5f + 0.5f;
        return LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ACCENT, 0.35f + 0.25f * pulse);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 制造过程中不允许ESC关闭
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !isCompleted) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public Screen getReturnScreen() {
        return returnScreen;
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
