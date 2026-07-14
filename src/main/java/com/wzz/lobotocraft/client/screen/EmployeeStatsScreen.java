package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.IAttributeItem;
import com.wzz.lobotocraft.item.ego.base.IMentalValueItem;
import com.wzz.lobotocraft.item.ego.base.IWorkBonusItem;
import com.wzz.lobotocraft.util.CuriosUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 员工属性界面
 * 显示四大属性：勇气、谨慎、自律、正义
 */
public class EmployeeStatsScreen extends Screen {

    private final Screen previousScreen;

    // 属性数据
    private int fortitude = 20;
    private int prudence = 20;
    private int temperance = 20;
    private int justice = 20;

    // 加成数据
    private float temperanceWorkSpeedBonus = 0; // 自律工作速度加成
    private float temperanceSuccessBonus = 0;   // 自律成功率加成

    // 勇气加成详细数据
    private float fortitudeHealthBonus = 0;     // 生命值加成
    private float fortitudeArmorBonus = 0;      // 护甲加成
    private float fortitudeToughnessBonus = 0;  // 护甲韧性加成

    // 谨慎加成详细数据
    private float prudenceMentalBonus = 0;      // 精神值加成

    // 正义速度加成详细数据
    private float justiceAttackSpeedBonus = 0;  // 攻击速度加成
    private float justiceMoveSpeedBonus = 0;    // 移动速度加成

    // 精神值加成数据
    private float maxMentalBonus = 0;           // 最大精神值加成
    private float mentalRegenPerSecond = 0;     // 精神值恢复速度（每秒）

    // 当前精神值数据
    private float currentMental = 0;
    private float maxMental = 20;

    public EmployeeStatsScreen(Screen previousScreen) {
        super(Component.literal("员工属性"));
        this.previousScreen = previousScreen;
        this.minecraft = net.minecraft.client.Minecraft.getInstance();

        // 从玩家获取属性数据
        if (minecraft.player != null) {
            // 获取员工属性
            minecraft.player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                fortitude = stats.getFortitude();
                prudence = stats.getPrudence();
                temperance = stats.getTemperance();
                justice = stats.getJustice();
            });

            // 获取精神值
            minecraft.player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                currentMental = mental.getMentalValue();
                maxMental = mental.getMaxMentalValue();
            });

            // 计算各种加成
            calculateAttributeBonuses();
            calculateWorkBonuses();
            calculateMentalBonuses();
        }
    }

    /**
     * 计算 IAttributeItem 提供的属性加成（更新为支持多属性）
     */
    private void calculateAttributeBonuses() {
        fortitudeHealthBonus = 0;
        fortitudeArmorBonus = 0;
        fortitudeToughnessBonus = 0;
        prudenceMentalBonus = 0;
        justiceAttackSpeedBonus = 0;
        justiceMoveSpeedBonus = 0;
        if (minecraft.player == null) return;

        for (ItemStack stack : CuriosUtil.getCuriosItems(minecraft.player)) {
            if (stack.getItem() instanceof IAttributeItem attributeItem && attributeItem.hasAttribute()) {
                // 使用新的多属性API
                List<AttributeEntry> entries = attributeItem.getAttributeEntries(minecraft.player);

                for (AttributeEntry entry : entries) {
                    double bonus = entry.getValue();

                    // 根据属性类型累加加成
                    if (entry.getAttribute() == Attributes.MAX_HEALTH) {
                        // 生命值 -> 勇气
                        fortitudeHealthBonus += (float) bonus;
                    } else if (entry.getAttribute() == Attributes.ARMOR) {
                        // 护甲 -> 勇气
                        fortitudeArmorBonus += (float) bonus;
                    } else if (entry.getAttribute() == Attributes.ARMOR_TOUGHNESS) {
                        // 护甲韧性 -> 勇气
                        fortitudeToughnessBonus += (float) bonus;
                    } else if (entry.getAttribute() == ModAttributes.EXTRA_MENTAL_VALUE.get()) {
                        // 精神值加成通常走 IMentalValueItem，但也可以在这里处理
                        prudenceMentalBonus += (float) bonus;
                    } else if (entry.getAttribute() == Attributes.ATTACK_SPEED) {
                        // 攻击速度 -> 正义
                        justiceAttackSpeedBonus += (float) bonus;
                    } else if (entry.getAttribute() == Attributes.MOVEMENT_SPEED) {
                        // 移动速度 -> 正义
                        justiceMoveSpeedBonus += (float) bonus;
                    }
                }
            }
        }
    }

    /**
     * 计算 IWorkBonusItem 提供的工作加成
     */
    private void calculateWorkBonuses() {
        temperanceWorkSpeedBonus = 0;
        temperanceSuccessBonus = 0;
        if (minecraft.player == null) return;
        for (ItemStack stack : CuriosUtil.getCuriosItems(minecraft.player)) {
            if (stack.getItem() instanceof IWorkBonusItem workBonusItem) {
                temperanceWorkSpeedBonus += workBonusItem.getWorkSpeedBonus(minecraft.player, null);
                temperanceSuccessBonus += workBonusItem.getWorkSuccessBonus(minecraft.player, null);
            }
        }
        for (ItemStack armorStack : minecraft.player.getArmorSlots()) {
            if (armorStack.getItem() instanceof IWorkBonusItem workBonusItem) {
                temperanceWorkSpeedBonus += workBonusItem.getWorkSpeedBonus(minecraft.player, null);
                temperanceSuccessBonus += workBonusItem.getWorkSuccessBonus(minecraft.player, null);
            }
        }
    }

    // ==================== 界面自适应缩放(只缩不放,不改变正常分辨率下的显示样式) ====================
    private static final int MIN_UI_WIDTH = 430;
    private static final int MIN_UI_HEIGHT = 400;
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

        // 主面板（增加高度以容纳精神值显示）
        int panelWidth = 400;
        int panelHeight = 380;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        // 面板背景
        graphics.fill(panelX - 3, panelY - 3,
                panelX + panelWidth + 3, panelY + panelHeight + 3, 0xFF8B4513);
        graphics.fill(panelX, panelY,
                panelX + panelWidth, panelY + panelHeight, 0xFF2C1810);

        // 标题
        graphics.drawCenteredString(this.font, "§l§6员工属性",
                centerX, panelY + 15, 0xFFD700);

        // 分隔线
        graphics.fill(panelX + 20, panelY + 35,
                panelX + panelWidth - 20, panelY + 37, 0xFF8B4513);

        // 员工等级
        int employeeLevel = Math.min(Math.min(
                        calculateLevel(fortitude), calculateLevel(prudence)),
                Math.min(calculateLevel(temperance), calculateLevel(justice)));

        graphics.drawCenteredString(this.font, "§e员工等级: §f" + employeeLevel,
                centerX, panelY + 50, 0xFFFFFF);

        // 精神值显示
        renderMentalValue(graphics, panelX + 30, panelY + 70);

        // 四大属性
        int attrY = panelY + 120;
        int attrSpacing = 55;

        // 勇气（显示生命值、护甲加成）
        renderAttributeWithFortitudeBonus(graphics, panelX + 30, attrY,
                "§c勇气 (Fortitude)", fortitude, 0xFF8B0000,
                "影响最大生命值");

        // 谨慎（显示精神值加成）
        renderAttributeWithPrudenceBonus(graphics, panelX + 30, attrY + attrSpacing,
                "§b谨慎 (Prudence)", prudence, 0xFF4169E1,
                "影响最大精神值");

        // 自律（显示工作加成）
        renderAttributeWithWorkBonus(graphics, panelX + 30, attrY + attrSpacing * 2,
                "§a自律 (Temperance)", temperance, 0xFF228B22,
                "影响工作成功率和工作速度");

        // 正义（显示速度加成）
        renderAttributeWithJusticeBonus(graphics, panelX + 30, attrY + attrSpacing * 3,
                "§6正义 (Justice)", justice, 0xFFDAA520,
                "影响攻击速度和移动速度");

        // 返回提示
        graphics.drawCenteredString(this.font, "§7按 ESC 或 E 返回",
                centerX, panelY + panelHeight - 20, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染精神值条
     */
    private void renderMentalValue(GuiGraphics graphics, int x, int y) {
        // 标题
        graphics.drawString(this.font, "§d精神值 (Mental)", x, y, 0xFFFFFF);

        // 获取实际的最大精神值（包括加成）
        float actualMaxMental = maxMental + maxMentalBonus;

        // 确保当前精神值不超过最大值
        float displayMental = Math.min(currentMental, actualMaxMental);

        // 数值显示
        String mentalText;
        if (maxMentalBonus > 0) {
            mentalText = String.format("%.1f / %.0f §a(+%.0f)§f",
                    displayMental, actualMaxMental, maxMentalBonus);
        } else {
            mentalText = String.format("%.1f / %.0f", displayMental, actualMaxMental);
        }
        graphics.drawString(this.font, "§f" + mentalText, x + 260, y, 0xFFFFFF);

        // 进度条 - 使用实际最大值
        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        // 背景
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF1A1A1A);

        // 填充
        float fillRatio = displayMental / actualMaxMental;
        int fillWidth = (int)(fillRatio * (barWidth - 4));

        // 根据精神值比例改变颜色
        int fillColor;
        if (fillRatio > 0.5f) {
            fillColor = 0xFFDA70D6; // 紫色（正常）
        } else if (fillRatio > 0.25f) {
            fillColor = 0xFFFFA500; // 橙色（警告）
        } else {
            fillColor = 0xFFFF0000; // 红色（危险）
        }

        graphics.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, fillColor);

        // 添加精神值恢复速度显示
        if (mentalRegenPerSecond > 0) {
            graphics.drawString(this.font, "§a恢复速度: " + String.format("%.1f", mentalRegenPerSecond) + "/秒",
                    x + 200, y + 30, 0xAAAAAA);
        }
    }

    private void calculateMentalBonuses() {
        maxMentalBonus = 0;
        mentalRegenPerSecond = 0;

        if (minecraft.player == null) return;

        minecraft.player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            maxMentalBonus = mental.getExtraMentalValue();
        });

        for (ItemStack stack : CuriosUtil.getCuriosItems(minecraft.player)) {
            if (stack.getItem() instanceof IMentalValueItem mentalItem && mentalItem.hasMentalBonus()) {
                mentalRegenPerSecond += mentalItem.getMentalRegenPerSecond(minecraft.player);
            }
        }
    }

    /**
     * 渲染勇气属性 - 显示生命值、护甲加成
     */
    private void renderAttributeWithFortitudeBonus(GuiGraphics graphics, int x, int y,
                                                   String name, int value, int barColor, String baseDescription) {
        graphics.drawString(this.font, name, x, y, 0xFFFFFF);

        int level = calculateLevel(value);
        String levelText = "Lv." + level;
        graphics.drawString(this.font, "§e" + levelText, x + 200, y, 0xFFD700);

        String valueText = value + " / " + EmployeeStats.MAX_STAT;
        graphics.drawString(this.font, "§f" + valueText, x + 260, y, 0xFFFFFF);

        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF1A1A1A);

        int fillWidth = (int)((value / 100.0f) * (barWidth - 4));
        graphics.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, barColor);

        for (int i = 1; i < 5; i++) {
            int dividerX = barX + (int)((i * 20 / 100.0f) * (barWidth - 4)) + 2;
            graphics.fill(dividerX, barY, dividerX + 1, barY + barHeight, 0xFF000000);
        }

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(baseDescription);

        if (fortitudeHealthBonus > 0 || fortitudeArmorBonus > 0 || fortitudeToughnessBonus > 0) {
            descBuilder.append(" ");
            boolean hasBonus = false;

            if (fortitudeHealthBonus > 0) {
                descBuilder.append("§c(+").append(String.format("%.0f", fortitudeHealthBonus)).append("生命值)");
                hasBonus = true;
            }
            if (fortitudeArmorBonus > 0) {
                if (hasBonus) descBuilder.append(" ");
                descBuilder.append("§c(+").append(String.format("%.0f", fortitudeArmorBonus)).append("护甲)");
                hasBonus = true;
            }
            if (fortitudeToughnessBonus > 0) {
                if (hasBonus) descBuilder.append(" ");
                descBuilder.append("§c(+").append(String.format("%.0f", fortitudeToughnessBonus)).append("韧性)");
            }
        }

        graphics.drawString(this.font, "§7" + descBuilder.toString(), x, y + 28, 0x888888);
    }

    /**
     * 渲染谨慎属性 - 显示精神值加成
     */
    private void renderAttributeWithPrudenceBonus(GuiGraphics graphics, int x, int y,
                                                  String name, int value, int barColor, String baseDescription) {
        graphics.drawString(this.font, name, x, y, 0xFFFFFF);

        int level = calculateLevel(value);
        String levelText = "Lv." + level;
        graphics.drawString(this.font, "§e" + levelText, x + 200, y, 0xFFD700);

        String valueText = value + " / " + EmployeeStats.MAX_STAT;
        graphics.drawString(this.font, "§f" + valueText, x + 260, y, 0xFFFFFF);

        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF1A1A1A);

        int fillWidth = (int)((value / 100.0f) * (barWidth - 4));
        graphics.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, barColor);

        for (int i = 1; i < 5; i++) {
            int dividerX = barX + (int)((i * 20 / 100.0f) * (barWidth - 4)) + 2;
            graphics.fill(dividerX, barY, dividerX + 1, barY + barHeight, 0xFF000000);
        }

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(baseDescription);

        if (prudenceMentalBonus > 0) {
            descBuilder.append(" §b(+").append(String.format("%.0f", prudenceMentalBonus)).append("精神值)");
        }

        graphics.drawString(this.font, "§7" + descBuilder.toString(), x, y + 28, 0x888888);
    }

    /**
     * 渲染自律属性 - 显示工作加成
     */
    private void renderAttributeWithWorkBonus(GuiGraphics graphics, int x, int y,
                                              String name, int value, int barColor, String baseDescription) {
        graphics.drawString(this.font, name, x, y, 0xFFFFFF);

        int level = calculateLevel(value);
        String levelText = "Lv." + level;
        graphics.drawString(this.font, "§e" + levelText, x + 200, y, 0xFFD700);

        String valueText = value + " / " + EmployeeStats.MAX_STAT;
        graphics.drawString(this.font, "§f" + valueText, x + 260, y, 0xFFFFFF);

        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF1A1A1A);

        int fillWidth = (int)((value / 100.0f) * (barWidth - 4));
        graphics.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, barColor);

        for (int i = 1; i < 5; i++) {
            int dividerX = barX + (int)((i * 20 / 100.0f) * (barWidth - 4)) + 2;
            graphics.fill(dividerX, barY, dividerX + 1, barY + barHeight, 0xFF000000);
        }

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(baseDescription);

        if (temperanceSuccessBonus > 0 || temperanceWorkSpeedBonus > 0) {
            boolean hasBonus = false;

            if (temperanceSuccessBonus > 0) {
                descBuilder.append(" §a(+").append(String.format("%.0f", temperanceSuccessBonus * 100)).append("%成功率)");
                hasBonus = true;
            }
            if (temperanceWorkSpeedBonus > 0) {
                if (hasBonus) descBuilder.append(" ");
                descBuilder.append("§a(+").append((int)temperanceWorkSpeedBonus).append("tick速度)");
            }
        }

        graphics.drawString(this.font, "§7" + descBuilder.toString(), x, y + 28, 0x888888);
    }

    /**
     * 渲染正义属性 - 显示速度加成
     */
    private void renderAttributeWithJusticeBonus(GuiGraphics graphics, int x, int y,
                                                 String name, int value, int barColor, String baseDescription) {
        graphics.drawString(this.font, name, x, y, 0xFFFFFF);

        int level = calculateLevel(value);
        String levelText = "Lv." + level;
        graphics.drawString(this.font, "§e" + levelText, x + 200, y, 0xFFD700);

        String valueText = value + " / " + EmployeeStats.MAX_STAT;
        graphics.drawString(this.font, "§f" + valueText, x + 260, y, 0xFFFFFF);

        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF1A1A1A);

        int fillWidth = (int)((value / 100.0f) * (barWidth - 4));
        graphics.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, barColor);

        for (int i = 1; i < 5; i++) {
            int dividerX = barX + (int)((i * 20 / 100.0f) * (barWidth - 4)) + 2;
            graphics.fill(dividerX, barY, dividerX + 1, barY + barHeight, 0xFF000000);
        }

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(baseDescription);

        if (justiceAttackSpeedBonus > 0 || justiceMoveSpeedBonus > 0) {
            boolean hasBonus = false;

            if (justiceAttackSpeedBonus > 0) {
                descBuilder.append(" §6(+").append(String.format("%.0f", justiceAttackSpeedBonus * 100)).append("%攻击速度)");
                hasBonus = true;
            }
            if (justiceMoveSpeedBonus > 0) {
                if (hasBonus) descBuilder.append(" ");
                descBuilder.append("§6(+").append(String.format("%.0f", justiceMoveSpeedBonus * 100)).append("%移动速度)");
            }
        }

        graphics.drawString(this.font, "§7" + descBuilder.toString(), x, y + 28, 0x888888);
    }

    /**
     * 计算属性等级
     */
    private int calculateLevel(int stat) {
        if (stat >= 100) return 5;
        if (stat >= 80) return 4;
        if (stat >= 60) return 3;
        if (stat >= 40) return 2;
        return 1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null && previousScreen != null) {
            minecraft.setScreen(previousScreen);
        } else {
            super.onClose();
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
