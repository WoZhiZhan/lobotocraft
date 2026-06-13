package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
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
    
    // 装备数据
    private final ResourceLocation equipmentIcon;
    private final String equipmentName;
    private final RiskLevel riskLevel;
    private final int cost;
    
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
        super(Component.literal("装备制造中..."));
        this.craftingType = craftingType;
        this.abnormalityCode = abnormalityCode;
        this.abnormalityId = abnormalityId;
        this.scrollOffset = scrollOffset;
        this.cost = cost;
        
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
        graphics.fill(0, 0, this.width, this.height, 0xD0000000);
        
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
        
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 绘制主面板背景
     */
    private void renderMainPanel(GuiGraphics graphics, int x, int y) {
        // 外边框 - 渐变橙色光效
        int glowColor = getGlowColor();
        graphics.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + PANEL_HEIGHT + 2, glowColor);
        
        // 主背景 - 深色半透明
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xE0151515);
        
        // 顶部装饰条
        graphics.fill(x, y, x + PANEL_WIDTH, y + 3, 0xFFFF8800);
        
        // 底部装饰条
        graphics.fill(x, y + PANEL_HEIGHT - 3, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF884400);
        
        // 内部边框
        graphics.fill(x + 5, y + 5, x + PANEL_WIDTH - 5, y + 6, 0x60FFAA00);
        graphics.fill(x + 5, y + PANEL_HEIGHT - 6, x + PANEL_WIDTH - 5, y + PANEL_HEIGHT - 5, 0x60FFAA00);
    }

    /**
     * 绘制装备图标
     */
    private void renderEquipmentIcon(GuiGraphics graphics, int x, int y, float partialTick) {
        int iconX = x + (PANEL_WIDTH - ICON_SIZE) / 2;
        int iconY = y + 20;
        
        // 图标背景 - 脉动光效
        float pulse = Mth.sin((craftingTicks + partialTick) * 0.1f) * 0.3f + 0.7f;
        int bgColor = (int)(pulse * 60) << 24 | 0x442200;
        graphics.fill(iconX - 5, iconY - 5, iconX + ICON_SIZE + 5, iconY + ICON_SIZE + 5, bgColor);
        
        // 边框
        graphics.fill(iconX - 5, iconY - 5, iconX + ICON_SIZE + 5, iconY - 4, 0xFFFFAA00);
        graphics.fill(iconX - 5, iconY + ICON_SIZE + 4, iconX + ICON_SIZE + 5, iconY + ICON_SIZE + 5, 0xFFFFAA00);
        graphics.fill(iconX - 5, iconY - 5, iconX - 4, iconY + ICON_SIZE + 5, 0xFFFFAA00);
        graphics.fill(iconX + ICON_SIZE + 4, iconY - 5, iconX + ICON_SIZE + 5, iconY + ICON_SIZE + 5, 0xFFFFAA00);
        
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
            int color = (int)(alpha * 200) << 24 | 0xFFDD00;
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
                x + PANEL_WIDTH / 2, infoY, 0xFFAAAAAA);
        
        // 装备名称
        graphics.drawCenteredString(this.font, equipmentName,
                x + PANEL_WIDTH / 2, infoY + 12, 0xFFFFFFFF);
        
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
        int barHeight = 20;
        
        // 进度条背景
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2A2A2A);
        
        // 进度条边框
        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFF666666);
        graphics.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFF666666);
        graphics.fill(barX - 1, barY, barX, barY + barHeight, 0xFF666666);
        graphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFF666666);
        
        // 计算进度
        float progress = (craftingTicks + partialTick) / CRAFTING_DURATION;
        progress = Mth.clamp(progress, 0f, 1f);
        int filledWidth = (int)(barWidth * progress);
        
        // 进度条填充 - 渐变效果
        if (filledWidth > 0) {
            // 底层 - 深橙色
            graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFCC6600);
            
            // 顶层光效 - 亮橙色
            int lightHeight = barHeight / 3;
            graphics.fill(barX, barY, barX + filledWidth, barY + lightHeight, 0xFFFFAA00);
            
            // 闪烁效果
            if (!isCompleted) {
                float shine = Mth.sin(glowAnimation) * 0.3f + 0.3f;
                int shineColor = (int)(shine * 128) << 24 | 0xFFFFFF;
                graphics.fill(barX, barY, barX + filledWidth, barY + 2, shineColor);
            }
        }
        
        // 进度百分比文字
        String progressText = String.format("%.0f%%", progress * 100);
        graphics.drawCenteredString(this.font, progressText,
                barX + barWidth / 2, barY + 6, 0xFFFFFFFF);
    }

    /**
     * 绘制状态文本
     */
    private void renderStatusText(GuiGraphics graphics, int x, int y) {
        int textY = y + 180;
        
        if (isCompleted) {
            graphics.drawCenteredString(this.font, "§a§l制造完成！",
                    x + PANEL_WIDTH / 2, textY, 0xFFFFFFFF);
        } else {
            int remainingSeconds = (CRAFTING_DURATION - craftingTicks) / 20 + 1;
            String statusText = String.format("制造中... %d秒", remainingSeconds);
            
            // 闪烁效果
            float alpha = Mth.sin(glowAnimation * 2) * 0.3f + 0.7f;
            int textColor = (int)(alpha * 255) << 24 | 0xFFDD00;
            
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
            new DevelopEquipmentPacket(abnormalityCode, craftingType, abnormalityId, scrollOffset)
        );
    }

    /**
     * 获取发光颜色（脉动效果）
     */
    private int getGlowColor() {
        float pulse = Mth.sin(glowAnimation) * 0.3f + 0.7f;
        int alpha = (int)(pulse * 180);
        return (alpha << 24) | 0xFF8800;
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
