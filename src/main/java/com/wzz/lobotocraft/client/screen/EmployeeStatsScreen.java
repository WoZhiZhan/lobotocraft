package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.IAttributeItem;
import com.wzz.lobotocraft.item.ego.base.IMentalValueItem;
import com.wzz.lobotocraft.item.ego.base.IWorkBonusItem;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 员工属性界面
 * 显示四大属性：勇气、谨慎、自律、正义
 */
public class EmployeeStatsScreen extends Screen {
    private static final int[] STAT_GRADE_THRESHOLDS = {30, 45, 65, 85, 100};

    // 四维主题图标（theme 贴图不存在时降级为纯色标）
    private static final ResourceLocation ICON_FORTITUDE =
            ResourceUtil.createInstance("textures/gui/theme/stat_fortitude.png");
    private static final ResourceLocation ICON_PRUDENCE =
            ResourceUtil.createInstance("textures/gui/theme/stat_prudence.png");
    private static final ResourceLocation ICON_TEMPERANCE =
            ResourceUtil.createInstance("textures/gui/theme/stat_temperance.png");
    private static final ResourceLocation ICON_JUSTICE =
            ResourceUtil.createInstance("textures/gui/theme/stat_justice.png");

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
        super(Component.translatable("screen.lobotocraft.employee_stats.title"));
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

        // 面板背景（脑叶主题：深底 + 描边 + 角括号 + 头部）
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        LobotomyUiRenderer.drawPanelHeader(graphics, this.font, panelX, panelY, panelWidth,
                "AGENT STATUS", tr("screen.lobotocraft.employee_stats.title"));

        // 员工等级
        int employeeLevel = EmployeeLevelCalculator.fromStatValues(
                fortitude, prudence, temperance, justice);
        String levelLabel = tr("screen.lobotocraft.employee_stats.employee_level");
        String levelValue = Integer.toString(employeeLevel);
        int levelX = centerX - (this.font.width(levelLabel) + this.font.width(levelValue)) / 2;
        graphics.drawString(this.font, levelLabel, levelX, panelY + 50,
                LobotomyUiTheme.TEXT_BODY, false);
        graphics.drawString(this.font, levelValue, levelX + this.font.width(levelLabel),
                panelY + 50, LobotomyUiTheme.GOLD, false);

        // 精神值显示
        renderMentalValue(graphics, panelX + 30, panelY + 70);

        // 四大属性
        int attrY = panelY + 120;
        int attrSpacing = 55;

        // 勇气（显示生命值、护甲加成）
        renderAttribute(graphics, panelX + 30, attrY,
                tr("screen.lobotocraft.employee_stats.fortitude"), ICON_FORTITUDE, fortitude,
                LobotomyUiTheme.STAT_FORTITUDE,
                tr("screen.lobotocraft.employee_stats.fortitude.description"),
                buildFortitudeBonusText());

        // 谨慎（显示精神值加成）
        renderAttribute(graphics, panelX + 30, attrY + attrSpacing,
                tr("screen.lobotocraft.employee_stats.prudence"), ICON_PRUDENCE, prudence,
                LobotomyUiTheme.STAT_PRUDENCE,
                tr("screen.lobotocraft.employee_stats.prudence.description"),
                buildPrudenceBonusText());

        // 自律（显示工作加成）
        renderAttribute(graphics, panelX + 30, attrY + attrSpacing * 2,
                tr("screen.lobotocraft.employee_stats.temperance"), ICON_TEMPERANCE, temperance,
                LobotomyUiTheme.STAT_TEMPERANCE,
                tr("screen.lobotocraft.employee_stats.temperance.description"),
                buildTemperanceBonusText());

        // 正义（显示速度加成）
        renderAttribute(graphics, panelX + 30, attrY + attrSpacing * 3,
                tr("screen.lobotocraft.employee_stats.justice"), ICON_JUSTICE, justice,
                LobotomyUiTheme.STAT_JUSTICE,
                tr("screen.lobotocraft.employee_stats.justice.description"),
                buildJusticeBonusText());

        // 返回提示
        String backHint = tr("screen.lobotocraft.employee_stats.back_hint");
        graphics.drawString(this.font, backHint, centerX - this.font.width(backHint) / 2,
                panelY + panelHeight - 20, LobotomyUiTheme.TEXT_MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染精神值条（SP 语义：白 → <50% 黄 → <25% 红）
     */
    private void renderMentalValue(GuiGraphics graphics, int x, int y) {
        // 标题
        graphics.drawString(this.font, tr("screen.lobotocraft.employee_stats.mental"),
                x, y, LobotomyUiTheme.BAR_SP, false);

        // 获取实际的最大精神值（包括加成）
        float actualMaxMental = maxMental + maxMentalBonus;

        // 确保当前精神值不超过最大值
        float displayMental = Math.min(currentMental, actualMaxMental);

        // 数值显示
        String mentalText = String.format("%.1f / %.0f", displayMental, actualMaxMental);
        graphics.drawString(this.font, mentalText, x + 260, y, LobotomyUiTheme.TEXT_TITLE, false);
        if (maxMentalBonus > 0) {
            graphics.drawString(this.font, String.format(" (+%.0f)", maxMentalBonus),
                    x + 260 + this.font.width(mentalText), y, LobotomyUiTheme.ENERGY_GREEN, false);
        }

        // 进度条 - 使用实际最大值
        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        // 根据精神值比例改变颜色（对齐全 mod SP 语义）
        float fillRatio = displayMental / actualMaxMental;
        int fillColor;
        if (fillRatio >= 0.5f) {
            fillColor = LobotomyUiTheme.BAR_SP;      // 白（正常）
        } else if (fillRatio >= 0.25f) {
            fillColor = LobotomyUiTheme.WARN_YELLOW; // 黄（警告）
        } else {
            fillColor = LobotomyUiTheme.BAR_SP_LOW;  // 红（濒临恐慌）
        }
        LobotomyUiRenderer.drawBar(graphics, barX, barY, barWidth, barHeight,
                displayMental, actualMaxMental, fillColor);

        // 添加精神值恢复速度显示
        if (mentalRegenPerSecond > 0) {
            String regenLabel = tr("screen.lobotocraft.employee_stats.regeneration");
            graphics.drawString(this.font, regenLabel, x + 200, y + 30,
                    LobotomyUiTheme.TEXT_MUTED, false);
            graphics.drawString(this.font, tr("screen.lobotocraft.employee_stats.per_second",
                            String.format(java.util.Locale.ROOT, "%.1f", mentalRegenPerSecond)),
                    x + 200 + this.font.width(regenLabel), y + 30,
                    LobotomyUiTheme.ENERGY_GREEN, false);
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
     * 渲染单项属性行：图标 + 名称（STAT_* 色）+ 等级（GOLD）+ 数值 + 主题条 + 说明/加成
     */
    private void renderAttribute(GuiGraphics graphics, int x, int y,
                                 String name, ResourceLocation icon, int value, int statColor,
                                 String baseDescription, String bonusText) {
        int nameX = x;
        if (LobotomyUiRenderer.textureExists(icon)) {
            LobotomyUiRenderer.blitScaled(graphics, icon, x, y, 8, 8, 16, 16);
            nameX = x + 12;
        }
        graphics.drawString(this.font, name, nameX, y, statColor, false);

        graphics.drawString(this.font, "Lv." + EmployeeLevelCalculator.statGrade(value),
                x + 200, y, LobotomyUiTheme.GOLD, false);

        String valueText = value + " / " + EmployeeStats.MAX_STAT;
        graphics.drawString(this.font, valueText, x + 260, y, LobotomyUiTheme.TEXT_TITLE, false);

        int barX = x;
        int barY = y + 13;
        int barWidth = 340;
        int barHeight = 12;

        LobotomyUiRenderer.drawBar(graphics, barX, barY, barWidth, barHeight,
                value, EmployeeStats.MAX_STAT, statColor);

        // Original grade boundaries, including the EX boundary at 100.
        for (int threshold : STAT_GRADE_THRESHOLDS) {
            int dividerX = barX + (int) ((threshold / (float) EmployeeStats.MAX_STAT)
                    * (barWidth - 4)) + 2;
            graphics.fill(dividerX, barY + 1, dividerX + 1, barY + barHeight - 1,
                    LobotomyUiTheme.LINE_FAINT);
        }

        graphics.drawString(this.font, baseDescription, x, y + 28,
                LobotomyUiTheme.TEXT_MUTED, false);
        if (!bonusText.isEmpty()) {
            graphics.drawString(this.font, bonusText,
                    x + this.font.width(baseDescription + " "), y + 28, statColor, false);
        }
    }

    private String buildFortitudeBonusText() {
        StringBuilder builder = new StringBuilder();
        if (fortitudeHealthBonus > 0) {
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.health",
                    String.format(java.util.Locale.ROOT, "%.0f", fortitudeHealthBonus)));
        }
        if (fortitudeArmorBonus > 0) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.armor",
                    String.format(java.util.Locale.ROOT, "%.0f", fortitudeArmorBonus)));
        }
        if (fortitudeToughnessBonus > 0) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.toughness",
                    String.format(java.util.Locale.ROOT, "%.0f", fortitudeToughnessBonus)));
        }
        return builder.toString();
    }

    private String buildPrudenceBonusText() {
        if (prudenceMentalBonus > 0) {
            return tr("screen.lobotocraft.employee_stats.bonus.mental",
                    String.format(java.util.Locale.ROOT, "%.0f", prudenceMentalBonus));
        }
        return "";
    }

    private String buildTemperanceBonusText() {
        StringBuilder builder = new StringBuilder();
        if (temperanceSuccessBonus > 0) {
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.success_rate",
                    String.format(java.util.Locale.ROOT, "%.0f", temperanceSuccessBonus * 100)));
        }
        if (temperanceWorkSpeedBonus > 0) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.work_speed",
                    (int) temperanceWorkSpeedBonus));
        }
        return builder.toString();
    }

    private String buildJusticeBonusText() {
        StringBuilder builder = new StringBuilder();
        if (justiceAttackSpeedBonus > 0) {
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.attack_speed",
                    String.format(java.util.Locale.ROOT, "%.0f", justiceAttackSpeedBonus * 100)));
        }
        if (justiceMoveSpeedBonus > 0) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(tr("screen.lobotocraft.employee_stats.bonus.movement_speed",
                    String.format(java.util.Locale.ROOT, "%.0f", justiceMoveSpeedBonus * 100)));
        }
        return builder.toString();
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

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
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
