package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.screen.api.AbnormalityMusicScreen;
import com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.UnlockManualPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import static com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_HEIGHT;
import static com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_SPACING;

public class AbnormalityEncyclopediaScreen extends AbnormalityMusicScreen {

    private static final ResourceLocation INSTINCT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_instinct.png");
    private static final ResourceLocation INSIGHT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_insight.png");
    private static final ResourceLocation ATTACHMENT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_attachment.png");
    private static final ResourceLocation REPRESSION_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_repression.png");

    private static final ResourceLocation DAMAGE_WHITE =
            ResourceUtil.createInstance("textures/particle/white.png");
    private static final ResourceLocation DAMAGE_RED =
            ResourceUtil.createInstance("textures/particle/red.png");
    private static final ResourceLocation DAMAGE_BLACK =
            ResourceUtil.createInstance("textures/particle/black.png");
    private static final ResourceLocation DAMAGE_PALE =
            ResourceUtil.createInstance("textures/particle/blue.png");

    private ResourceLocation abnormalityImage;

    private final int abnormalityId;
    private final String abnormalityCode;
    private final String abnormalityName;
    private final RiskLevel riskLevel;
    private final String damageType;
    private final int maxPEOutput;
    private final float[][] workPreferences;
    private int observationLevel;
    private final boolean isToolType;  // 是否为工具类异想体

    private final String[] manualTexts;  // 动态数量的管理须知
    private final String sensitiveInfoText;

    private boolean basicInfoUnlocked;       // 基础信息（包含图片）
    private final boolean[] workPreferencesUnlocked; // 工作偏好
    private boolean sensitiveInfoUnlocked;   // 敏感信息
    private final boolean[] manualsUnlocked;       // 动态数量的管理须知

    // 解锁成本
    private final int basicInfoCost;
    private final int workPreferencesCost;
    private final int sensitiveInfoCost;
    private final int manualCost;  // 所有管理须知使用相同成本

    private int playerEnergy;
    private final int developmentArmorCount;
    private final int developmentWeaponCount;

    // 滚动相关
    private double scrollOffset;
    private double targetScrollOffset;

    // 滚动条拖动相关
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private double dragStartScroll = 0;

    private int realMouseX;
    private int realMouseY;

    private String message;
    private int showTick;
    private int maxShowTick = 40;

    public AbnormalityEncyclopediaScreen(int abnormalityId, String code, String name,
                                         RiskLevel riskLevel, String damageType, int maxPEOutput,
                                         float[][] workPreferences, int observationLevel,
                                         String[] manualTexts, String sensitiveInfoText,
                                         boolean basicInfo, boolean[] workPrefsUnlocked, boolean sensitiveInfo,
                                         boolean[] manualsUnlocked,
                                         int basicInfoCost, int workPreferencesCost,
                                         int sensitiveInfoCost, int manualCost, int developmentWeaponCount, int developmentArmorCount, double scrollOffset, boolean isToolType) {
        super(Component.literal("异想体图鉴"), abnormalityId);
        this.minecraft = Minecraft.getInstance();
        this.abnormalityId = abnormalityId;
        this.abnormalityCode = code;
        this.abnormalityName = name;
        this.riskLevel = riskLevel;
        this.damageType = damageType;
        this.maxPEOutput = maxPEOutput;
        this.workPreferences = workPreferences;
        this.observationLevel = observationLevel;
        this.manualTexts = manualTexts;
        this.sensitiveInfoText = sensitiveInfoText;
        this.basicInfoUnlocked = basicInfo;
        this.workPreferencesUnlocked = workPrefsUnlocked;
        this.sensitiveInfoUnlocked = sensitiveInfo;
        this.manualsUnlocked = manualsUnlocked;
        this.basicInfoCost = basicInfoCost;
        this.workPreferencesCost = workPreferencesCost;
        this.sensitiveInfoCost = sensitiveInfoCost;
        this.manualCost = manualCost;
        this.developmentWeaponCount = developmentWeaponCount;
        this.developmentArmorCount = developmentArmorCount;
        this.targetScrollOffset = scrollOffset;
        this.isToolType = isToolType;
        loadAbnormalityImage();
    }

    private void loadAbnormalityImage() {
        // 根据异想体代码加载对应图片
        // 例如 O-03-03 对应 textures/entities/abnormality/o_03_03.png
        String imagePath = "textures/entities/abnormality/" +
                abnormalityCode.toLowerCase().replace("-", "_") + ".png";
        this.abnormalityImage = ResourceUtil.createInstance(imagePath);
    }

    @Override
    public void init() {
        super.init();
        loadPlayerEnergy();
    }

    private void loadPlayerEnergy() {
        if (minecraft != null && minecraft.player != null) {
            // 统计背包里当前异想体的PE-BOX数量
            this.playerEnergy = PEBoxItem.countPEBoxes(
                    minecraft.player.getInventory(),
                    this.abnormalityCode
            );
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (message != null && !message.isEmpty()) {
            showTick++;
            if (showTick >= maxShowTick) {
                message = null;
                showTick = 0;
            }
        }
        // 每秒更新一次PE-BOX数量（防止玩家丢弃/获得PE-BOX时显示不同步）
        if (this.minecraft != null && this.minecraft.level != null &&
                this.minecraft.player != null && this.minecraft.level.getGameTime() % 20 == 0) {
            loadPlayerEnergy();
        }
    }

    // ==================== 界面自适应缩放(只缩不放,正常分辨率下显示样式完全不变) ====================
    private static final int MIN_UI_WIDTH = 500;
    private static final int MIN_UI_HEIGHT = 300;
    private float uiScale = 1f;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        uiScale = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.fitScale(this.width, this.height, MIN_UI_WIDTH, MIN_UI_HEIGHT);
        if (uiScale < 1f) {
            // 先铺满与图鉴背景同色的纯黑底,缩小后四周与界面背景视觉无缝
            graphics.fill(0, 0, this.width, this.height, EncyclopediaGUIConfig.Colors.BACKGROUND_BLACK);
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
        // 平滑滚动
        scrollOffset += (targetScrollOffset - scrollOffset) * EncyclopediaGUIConfig.Animation.SCROLL_SMOOTHNESS;

        // 全屏黑色背景
        graphics.fill(0, 0, this.width, this.height, EncyclopediaGUIConfig.Colors.BACKGROUND_BLACK);
        graphics.fill(0, 0, this.width, 3, EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        // 顶部标题栏（固定）
        renderTitleBar(graphics);

        // 开启裁剪区域（只渲染中间可滚动部分）
        int viewportTop = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT;
        int viewportBottom = this.height - EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT;
        int viewportHeight = viewportBottom - viewportTop;
        this.realMouseX = mouseX;
        this.realMouseY = mouseY;
        graphics.enableScissor(0, viewportTop, this.width, viewportBottom);

        // 应用滚动偏移
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        // 渲染可滚动内容
        renderScrollableContent(graphics, mouseX, (int)(mouseY + scrollOffset));

        graphics.pose().popPose();
        graphics.disableScissor();

        // 底部状态栏（固定）
        renderBottomBar(graphics);

        // 滚动条指示器
        renderScrollbar(graphics, viewportTop, viewportHeight, mouseX, mouseY);
        renderMessage(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTitleBar(GuiGraphics graphics) {
        // 根据是否解锁基础信息显示不同内容
        String displayText = basicInfoUnlocked ? (abnormalityCode + " - " + abnormalityName) : abnormalityCode;
        graphics.drawString(this.font, displayText,
                EncyclopediaGUIConfig.Layout.TOP_BAR_TITLE_X,
                EncyclopediaGUIConfig.Layout.TOP_BAR_TITLE_Y,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);
        graphics.drawString(this.font, riskLevel.name(),
                this.width - EncyclopediaGUIConfig.Layout.TOP_BAR_RISK_OFFSET_X,
                EncyclopediaGUIConfig.Layout.TOP_BAR_TITLE_Y,
                riskLevel.getColor());
    }

    private void renderScrollableContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT + 5;

        renderLeftColumn(graphics, startY);
        renderCenterColumn(graphics, mouseX, mouseY, startY);
        renderRightColumn(graphics, mouseX, mouseY, startY);
    }

    private void renderLeftColumn(GuiGraphics graphics, int startY) {
        int x = EncyclopediaGUIConfig.Layout.LeftColumn.X;
        int y = startY;

        // 观察等级加成
        IAbnormality abnormality = this.abnormality;

        // 观察等级加成 - 使用异想体的配置
        if (!isToolType && abnormality != null) {
            String[] bonusTexts = abnormality.getObservationBonusTexts();
            for (int i = 0; i < 4; i++) {
                int color = i < observationLevel ?
                        EncyclopediaGUIConfig.Colors.TEXT_HIGHLIGHT :
                        EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY;
                graphics.drawString(this.font,
                        bonusTexts[i],
                        x,
                        y + i * EncyclopediaGUIConfig.Layout.LeftColumn.BONUS_LINE_SPACING,
                        color);
            }
        }
        // 观察等级
        int obsY = y;
        if (!isToolType)
            obsY += EncyclopediaGUIConfig.Layout.LeftColumn.OBSERVATION_LEVEL_Y_OFFSET;
        graphics.drawString(this.font,
                EncyclopediaGUIConfig.Text.TITLE_OBSERVATION_LEVEL,
                x, obsY, EncyclopediaGUIConfig.Colors.THEME_YELLOW);

        graphics.pose().pushPose();
        graphics.pose().translate(
                x + EncyclopediaGUIConfig.Layout.LeftColumn.OBSERVATION_LEVEL_X_OFFSET,
                obsY, 0);
        graphics.pose().scale(
                EncyclopediaGUIConfig.Layout.LeftColumn.OBSERVATION_LEVEL_SCALE,
                EncyclopediaGUIConfig.Layout.LeftColumn.OBSERVATION_LEVEL_SCALE,
                1.0f);
        graphics.drawString(this.font, String.valueOf(observationLevel),
                0, 0, EncyclopediaGUIConfig.Colors.THEME_YELLOW);
        graphics.pose().popPose();
    }

    private void renderCenterColumn(GuiGraphics graphics, int mouseX, int mouseY, int startY) {
        int x = EncyclopediaGUIConfig.Layout.CenterColumn.X;
        int y = startY;

        graphics.drawString(this.font,
                EncyclopediaGUIConfig.Text.TITLE_BASIC_INFO,
                x, y, EncyclopediaGUIConfig.Colors.THEME_ORANGE);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 异想体图片框
        int imgSize = EncyclopediaGUIConfig.Layout.CenterColumn.IMAGE_SIZE;
        graphics.fill(x, y, x + imgSize, y + imgSize,
                EncyclopediaGUIConfig.Colors.BACKGROUND_DARK);
        graphics.fill(x, y, x + imgSize, y + EncyclopediaGUIConfig.Layout.CenterColumn.IMAGE_BORDER_WIDTH,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        if (basicInfoUnlocked) {
            // 显示异想体图片
            try {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.blit(abnormalityImage,
                        x, y,
                        0, 0,
                        imgSize, imgSize,
                        imgSize, imgSize);
            } catch (Exception e) {
                // 如果图片加载失败，显示占位符
                graphics.drawCenteredString(this.font,
                        EncyclopediaGUIConfig.Text.PLACEHOLDER_IMAGE,
                        x + imgSize/2, y + imgSize/2 - 5,
                        EncyclopediaGUIConfig.Colors.TEXT_WHITE);
            }
        } else {
            // 未解锁：显示黑色遮罩和解锁提示
            graphics.fill(x, y, x + imgSize, y + imgSize,
                    EncyclopediaGUIConfig.Colors.BACKGROUND_OVERLAY);

            graphics.drawCenteredString(this.font,
                    String.format(EncyclopediaGUIConfig.Text.REQUIRE_PEBOX,
                            basicInfoCost,
                            abnormalityName),
                    x + imgSize/2, y + imgSize/2 - 5,
                    EncyclopediaGUIConfig.Colors.TEXT_WARNING);
        }

        graphics.drawCenteredString(this.font, abnormalityName,
                x + imgSize/2, y + imgSize + 10,
                EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 基础信息部分
        int infoY = y + imgSize + EncyclopediaGUIConfig.Layout.CenterColumn.BASIC_INFO_Y_OFFSET - imgSize;

        if (basicInfoUnlocked) {
            // 已解锁：显示详细信息
            renderBasicInfoUnlocked(graphics, x, infoY);
        } else {
            // 未解锁：显示遮罩
            renderBasicInfoLocked(graphics, x, infoY);
        }

        // 工作偏好 - 工具类异想体不显示
        if (!isToolType) {
            int workPrefStartY = infoY + EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_Y_OFFSET - 240;
            renderWorkPreferences(graphics, x, workPrefStartY, workPreferencesUnlocked);

            // ===== 3D 实体渲染（工作偏好下方，解锁基础信息后显示）=====
            if (basicInfoUnlocked) {
                // 两行工作偏好各90px，加标题间距
                int entityAreaY = workPrefStartY
                        + EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING
                        + 2 * 90 + 10;
                renderAbnormality3D(graphics, x + imgSize / 2, entityAreaY);
            }
        }
    }

    /**
     * 渲染异想体3D实体，跟随鼠标朝向
     */
    private void renderAbnormality3D(GuiGraphics graphics, int centerX, int topY) {
        if (!(this.abnormality instanceof LivingEntity livingEntity)) return;

        // 展示框尺寸
        int boxWidth  = 120;
        int boxHeight = 100;
        int boxLeft   = centerX - boxWidth  / 2;
        int boxTop    = topY;

        // 实体显示中心（框内偏下，留头顶空间）
        int entityDrawX = centerX;
        int entityDrawY = boxTop + boxHeight;

        // 根据实体 AABB 高度自适应 scale，避免巨型实体撑破框
        float entityHeight = livingEntity.getBbHeight();
        // 目标占框高度的 70%
        int scale = (int) Math.min(40, (boxHeight * 0.68f) / Math.max(entityHeight, 0.1f));
        scale = Math.max(scale, 10); // 最小不低于10

        // 鼠标朝向（取反让实体正向看鼠标）
        int entityScreenY = (int)(entityDrawY - scrollOffset);
        float mouseOffsetX = -(realMouseX - entityDrawX);
        float mouseOffsetY = -(realMouseY - entityScreenY);

        // 背景框
        graphics.fill(boxLeft - 2, boxTop - 2, boxLeft + boxWidth + 2, boxTop + boxHeight + 2,
                0x88000000);

        // 注意：scissor 用的是屏幕坐标，需要减去 scrollOffset
        int scissorTop    = (int)(boxTop - 50 - scrollOffset);
        int scissorBottom = (int)(boxTop    - scrollOffset + boxHeight + 20);
        // 确保不超出整个视口
        scissorTop    = Math.max(scissorTop,    EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT);
        scissorBottom = Math.min(scissorBottom, this.height - EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT);

        if (scissorBottom <= scissorTop) return; // 框被滚动到视口外，不渲染

        graphics.enableScissor(boxLeft, scissorTop, boxLeft + boxWidth, scissorBottom);

        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    entityDrawX,
                    entityDrawY,
                    scale,
                    mouseOffsetX,
                    mouseOffsetY,
                    livingEntity
            );
        } catch (Exception e) {
            graphics.drawCenteredString(this.font, "[?]",
                    centerX, boxTop + boxHeight / 2,
                    EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY);
        }

        graphics.disableScissor();

        graphics.drawCenteredString(this.font, "3D预览",
                centerX, boxTop + boxHeight + 20, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
    }

    private void renderBasicInfoUnlocked(GuiGraphics graphics, int x, int infoY) {
        // 风险等级
        graphics.drawString(this.font,
                EncyclopediaGUIConfig.Text.LABEL_RISK_LEVEL,
                x + 10, infoY,
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // 伤害类型
        infoY += EncyclopediaGUIConfig.Layout.CenterColumn.INFO_LINE_SPACING;
        graphics.drawString(this.font,
                EncyclopediaGUIConfig.Text.LABEL_DAMAGE_TYPE,
                x + 10, infoY,
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(getDamageTypeIcon(),
                x + 90, infoY - 5,
                0, 0,
                EncyclopediaGUIConfig.Layout.CenterColumn.INFO_ICON_SIZE,
                EncyclopediaGUIConfig.Layout.CenterColumn.INFO_ICON_SIZE,
                EncyclopediaGUIConfig.Layout.CenterColumn.INFO_ICON_SIZE,
                EncyclopediaGUIConfig.Layout.CenterColumn.INFO_ICON_SIZE);
        graphics.drawString(this.font, damageType,
                x + 110, infoY,
                EncyclopediaGUIConfig.Colors.getDamageTypeColor(damageType));

        if (!isToolType) {
            infoY += EncyclopediaGUIConfig.Layout.CenterColumn.INFO_LINE_SPACING;
            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.LABEL_MAX_OUTPUT,
                    x + 10, infoY,
                    EncyclopediaGUIConfig.Colors.TEXT_GRAY);

            graphics.pose().pushPose();
            graphics.pose().translate(x + 90, infoY, 0);
            graphics.pose().scale(1.2f, 1.2f, 1.0f);
            if (maxPEOutput == -1) {
                graphics.drawString(this.font, "/",
                        0, 0, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
            } else {
                graphics.drawString(this.font, String.valueOf(maxPEOutput),
                        0, 0, EncyclopediaGUIConfig.Colors.THEME_YELLOW);
            }
            graphics.pose().popPose();

            // PE产量详情
            infoY += EncyclopediaGUIConfig.Layout.CenterColumn.PE_RANGES_Y_OFFSET;
            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.TITLE_PE_OUTPUT,
                    x, infoY,
                    EncyclopediaGUIConfig.Colors.THEME_ORANGE);
            infoY += 12;

            // 根据异想体配置显示产量范围
            int goodMin = (int)(maxPEOutput * 0.8);
            int goodMax = maxPEOutput;
            int normalMin = (int)(maxPEOutput * 0.4);
            int normalMax = (int)(maxPEOutput * 0.7);
            int badMin = 0;
            int badMax = (int)(maxPEOutput * 0.3);

            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.PE_RANGE_GOOD + ": " + goodMin + "-" + goodMax,
                    x + 10, infoY,
                    EncyclopediaGUIConfig.Colors.STATUS_GREEN);
            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.PE_RANGE_NORMAL + ": " + normalMin + "-" + normalMax,
                    x + 80, infoY,
                    EncyclopediaGUIConfig.Colors.STATUS_YELLOW);
            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.PE_RANGE_BAD + ": " + badMin + "-" + badMax,
                    x + 150, infoY,
                    EncyclopediaGUIConfig.Colors.STATUS_RED);
        }
    }

    private void renderBasicInfoLocked(GuiGraphics graphics, int x, int infoY) {
        // 显示黑色遮罩区域
        int lockHeight = 120;
        graphics.fill(x, infoY, x + 180, infoY + lockHeight,
                EncyclopediaGUIConfig.Colors.BACKGROUND_OVERLAY);

        // 显示锁定文本
        graphics.drawCenteredString(this.font,
                EncyclopediaGUIConfig.Text.LOCKED_CONTENT,
                x + 90, infoY + 20,
                EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY);
        graphics.drawCenteredString(this.font,
                EncyclopediaGUIConfig.Text.LOCKED_CONTENT,
                x + 90, infoY + 40,
                EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY);
        graphics.drawCenteredString(this.font,
                EncyclopediaGUIConfig.Text.LOCKED_CONTENT,
                x + 90, infoY + 60,
                EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY);
    }

    /**
     * 渲染E.G.O饰品框（在预留位置）
     */
    private void renderEGOGift(GuiGraphics graphics, int x, int y, IAbnormality abnormality) {
        if (abnormality == null) return;

        EGOEquipmentData.GiftData giftData = abnormality.getEGOGiftData();
        if (giftData == null) return;

        // 渲染图标背景框（70x70）
        graphics.fill(x, y, x + 70, y + 70, 0xFF3A3A3A);

        ResourceLocation resourceLocation = giftData.iconTexture();
        if (resourceLocation != null && ResourceUtil.exists(resourceLocation)) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().scale(abnormality.getGiftRenderScale()[0], abnormality.getGiftRenderScale()[1], abnormality.getGiftRenderScale()[2]);
            graphics.pose().translate(abnormality.getGiftRenderOffset()[0], abnormality.getGiftRenderOffset()[1], abnormality.getGiftRenderOffset()[2]);
            graphics.blit(resourceLocation, x, y, 0, 0, 70, 70, 70, 70);
        } else graphics.fill(x, y, x + 70, y + 70, 0xFF664422);

        // 渲染饰品信息
        int textX = x + 80;
        int textY = y + 5;

        // 名称
        graphics.drawString(this.font, giftData.name(), textX, textY,
                EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 效果描述（多行）
        textY += 15;
        for (String effect : giftData.effects()) {
            graphics.drawString(this.font, effect, textX, textY,
                    EncyclopediaGUIConfig.Colors.TEXT_GRAY);
            textY += 12;
        }
    }

    /**
     * 渲染E.G.O武器框（在预留位置）
     */
    private void renderEGOWeapon(GuiGraphics graphics, int x, int y, IAbnormality abnormality) {
        if (abnormality == null) return;

        EGOEquipmentData.WeaponData weaponData = abnormality.getEGOWeaponData();
        if (weaponData == null) return;

        int width = 150, height = 230;

        // 背景框
        graphics.fill(x, y, x + width, y + height, 0xFF331111);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF1A0808);

        // 标题
        graphics.drawCenteredString(this.font, "E.G.O武器", x + width/2, y + 8,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        // 渲染武器图标（居中，80x80）
        int iconSize = 80;
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + 25;

        ResourceLocation resourceLocation = weaponData.iconTexture();
        if (resourceLocation != null && ResourceUtil.exists(resourceLocation)) {
            graphics.pose().pushPose();
            graphics.pose().translate(iconX - 55, iconY, 0);
            graphics.pose().scale(2.0f, 1.0f, 1.0f);
            graphics.pose().scale(abnormality.getWeaponRenderScale()[0], abnormality.getWeaponRenderScale()[1], abnormality.getWeaponRenderScale()[2]);
            graphics.pose().translate(abnormality.getWeaponRenderOffset()[0], abnormality.getWeaponRenderOffset()[1], abnormality.getWeaponRenderOffset()[2]);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(resourceLocation, 0, 0, 0, 0,
                    iconSize, iconSize,   // 渲染尺寸
                    iconSize, iconSize);  // 纹理尺寸
            graphics.pose().popPose();
        } else {
            graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF664422);
        }

        // 风险等级
        graphics.drawCenteredString(this.font, weaponData.riskLevel().name(),
                x + width/2, iconY + iconSize + 5,
                weaponData.riskLevel().getColor());

        // 武器名称
        graphics.drawCenteredString(this.font, weaponData.name(),
                x + width/2, iconY + iconSize + 17,
                EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 属性信息
        int attrY = iconY + iconSize + 35;
        int attrX = x + 10;

        // 属性
        String damageType = weaponData.damageType();
        if ("ALL".equals(damageType)) {
            // 全属性：叠加显示4个图标
            String[] allTypes = {"red", "blue", "white", "black"};
            int iconDamageTypeSize = 12;
            int startX = attrX + 30;
            int startY = attrY - 3;
            int offset = 8; // 每个图标偏移量，产生叠放效果

            for (int i = 0; i < allTypes.length; i++) {
                ResourceLocation icon = getDamageTypeIcon(allTypes[i]);
                graphics.blit(icon,
                        startX + i * offset, startY + (i % 2 == 0 ? 0 : 2), // 奇偶错开更自然
                        0, 0, iconDamageTypeSize, iconDamageTypeSize, iconDamageTypeSize, iconDamageTypeSize);
            }

            // 绘制"全属性"文字
            graphics.drawString(this.font, "全属性",
                    attrX + 30 + offset * 3 + 5, attrY,
                    EncyclopediaGUIConfig.Colors.getDamageTypeColor("WHITE"));
        } else {
            // 单一属性：正常显示
            ResourceLocation damageIcon = getDamageTypeIcon(weaponData.damageType());
            graphics.blit(damageIcon, attrX + 30, attrY - 3, 0, 0, 12, 12, 12, 12);
            graphics.drawString(this.font, weaponData.damageType(),
                    attrX + 45, attrY, EncyclopediaGUIConfig.Colors.getDamageTypeColor(weaponData.damageType()));
        }

        // 攻击力
        attrY += 15;
        graphics.drawString(this.font, "攻击力",
                attrX, attrY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, weaponData.attackPower(),
                attrX + 60, attrY, EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 攻击速度
        attrY += 15;
        graphics.drawString(this.font, "攻击速度",
                attrX, attrY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, weaponData.attackSpeed(),
                attrX + 60, attrY, EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 攻击距离
        attrY += 15;
        graphics.drawString(this.font, "攻击距离",
                attrX, attrY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, weaponData.attackRange(),
                attrX + 60, attrY, EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 研发进度（底部）
        graphics.fill(x + 5, y + height - 20, x + width - 5, y + height - 5, 0x88446622);
        String progress = developmentWeaponCount + "/" + weaponData.developmentMaxCount();
        graphics.drawString(this.font, progress,
                x + 10, y + height - 16,
                developmentWeaponCount >= weaponData.developmentMaxCount() ?
                        0xFFFFAA00 : EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, "研发",
                x + width - 30, y + height - 16, 0xFFFFAA00);
    }

    /**
     * 渲染E.G.O护甲框（在预留位置）
     */
    private void renderEGOArmor(GuiGraphics graphics, int x, int y, IAbnormality abnormality) {
        if (abnormality == null) return;

        EGOEquipmentData.ArmorData armorData = abnormality.getEGOArmorData();
        if (armorData == null) return;

        int width = 150, height = 230;

        // 背景框
        graphics.fill(x, y, x + width, y + height, 0xFF113333);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF081A1A);

        // 标题
        graphics.drawCenteredString(this.font, "E.G.O护甲", x + width/2, y + 8,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        // 渲染护甲图标（居中，80x80）
        int iconSize = 80;
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + 25;

        ResourceLocation resourceLocation = armorData.iconTexture();
        if (resourceLocation != null && ResourceUtil.exists(resourceLocation)) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().scale(abnormality.getArmorRenderScale()[0], abnormality.getArmorRenderScale()[1], abnormality.getArmorRenderScale()[2]);
            graphics.pose().translate(abnormality.getArmorRenderOffset()[0], abnormality.getArmorRenderOffset()[1], abnormality.getArmorRenderOffset()[2]);
            graphics.blit(resourceLocation, iconX, iconY, 0, 0,
                    iconSize, iconSize, iconSize, iconSize);
        } else {
            graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF664422);
        }

        // 风险等级
        graphics.drawCenteredString(this.font, armorData.riskLevel().name(),
                x + width/2, iconY + iconSize + 5,
                armorData.riskLevel().getColor());

        // 护甲名称
        graphics.drawCenteredString(this.font, armorData.name(),
                x + width/2, iconY + iconSize + 17,
                EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 抗性信息
        int resY = iconY + iconSize + 35;
        int resX = x + 8;
        int lineHeight = 13;

        // RED抗性
        graphics.blit(DAMAGE_RED, resX, resY - 3, 0, 0, 10, 10, 10, 10);
        String redText = String.format("RED (%.1f)", armorData.redResistance());
        graphics.drawString(this.font, redText,
                resX + 13, resY, 0xFFFF6666);
        graphics.drawString(this.font, getResistanceLevel(armorData.redResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // WHITE抗性
        resY += lineHeight;
        graphics.blit(DAMAGE_WHITE, resX, resY - 3, 0, 0, 10, 10, 10, 10);
        String whiteText = String.format("WHITE (%.1f)", armorData.whiteResistance());
        graphics.drawString(this.font, whiteText,
                resX + 13, resY, 0xFFFFFFFF);
        graphics.drawString(this.font, getResistanceLevel(armorData.whiteResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // BLACK抗性
        resY += lineHeight;
        graphics.blit(DAMAGE_BLACK, resX, resY - 3, 0, 0, 10, 10, 10, 10);
        String blackText = String.format("BLACK (%.1f)", armorData.blackResistance());
        graphics.drawString(this.font, blackText,
                resX + 13, resY, 0xFFAA66FF);
        graphics.drawString(this.font, getResistanceLevel(armorData.blackResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // PALE抗性
        resY += lineHeight;
        graphics.blit(DAMAGE_PALE, resX, resY - 3, 0, 0, 10, 10, 10, 10);
        String paleText = String.format("BLUE (%.1f)", armorData.paleResistance());
        graphics.drawString(this.font, paleText,
                resX + 13, resY, 0xFF66DDFF);
        graphics.drawString(this.font, getResistanceLevel(armorData.paleResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // 研发进度（底部）
        graphics.fill(x + 5, y + height - 20, x + width - 5, y + height - 5, 0x88446622);
        String progress = developmentArmorCount + "/" + armorData.developmentMaxCount();
        graphics.drawString(this.font, progress,
                x + 10, y + height - 16,
                developmentArmorCount >= armorData.developmentMaxCount() ?
                        0xFFFFAA00 : EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, "研发",
                x + width - 30, y + height - 16, 0xFFFFAA00);
    }

    /**
     * 获取抗性等级文本（简化版）
     */
    private String getResistanceLevel(float value) {
        if (value >= 2.0f) return "抗性极低";
        if (value >= 1.5f) return "抗性很低";
        if (value > 1.0f) return "抗性较低";
        if (value == 1.0f) return "抗性普通";
        if (value >= 0.8f) return "抗性较高";
        if (value >= 0.5f) return "抗性很高";
        return "抗性极高";
    }

    private ResourceLocation getDamageTypeIcon() {
        return getDamageTypeIcon(damageType);
    }

    private ResourceLocation getDamageTypeIcon(String damageType) {
        return switch (damageType.toUpperCase()) {
            case "RED" -> DAMAGE_RED;
            case "WHITE" -> DAMAGE_WHITE;
            case "BLACK" -> DAMAGE_BLACK;
            case "BLUE" -> DAMAGE_PALE;
            default -> DAMAGE_WHITE;
        };
    }

    private void renderWorkPreferences(GuiGraphics graphics, int x, int y, boolean[] unlockedStates) {
        graphics.drawString(this.font,
                EncyclopediaGUIConfig.Text.TITLE_WORK_PREF,
                x, y,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        ResourceLocation[] icons = {
                INSTINCT_ICON, INSIGHT_ICON, ATTACHMENT_ICON, REPRESSION_ICON
        };
        String[] names = {
                EncyclopediaGUIConfig.Text.WORK_TYPE_INSTINCT,
                EncyclopediaGUIConfig.Text.WORK_TYPE_INSIGHT,
                EncyclopediaGUIConfig.Text.WORK_TYPE_ATTACHMENT,
                EncyclopediaGUIConfig.Text.WORK_TYPE_REPRESSION
        };

        for (int work = 0; work < 4; work++) {
            int col = work % 2;
            int row = work / 2;

            int workX = x + col * 180;
            int workY = y + row * 90;

            boolean isUnlocked = unlockedStates[work];

            if (!isUnlocked) {
                // 未解锁：显示遮罩
                int lockHeight = 70;
                int lockWidth = 170;
                graphics.fill(workX, workY, workX + lockWidth, workY + lockHeight,
                        EncyclopediaGUIConfig.Colors.BACKGROUND_OVERLAY);

                // 显示图标（半透明）
                RenderSystem.setShaderColor(0.3F, 0.3F, 0.3F, 0.5F);
                graphics.blit(icons[work], workX, workY, 0, 0,
                        EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                        EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                        EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                        EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                graphics.drawString(this.font, names[work],
                        workX + 25, workY - 5,
                        EncyclopediaGUIConfig.Colors.TEXT_DARK_GRAY);

                graphics.drawCenteredString(this.font,
                        String.format(EncyclopediaGUIConfig.Text.REQUIRE_PEBOX,
                                workPreferencesCost,
                                abnormalityName),
                        workX + lockWidth / 2, workY + 30,
                        EncyclopediaGUIConfig.Colors.TEXT_WARNING);
                continue;
            }

            // 已解锁：显示完整信息
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(icons[work], workX, workY, 0, 0,
                    EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                    EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                    EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE,
                    EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_ICON_SIZE);

            graphics.drawString(this.font, names[work],
                    workX + 25, workY - 5,
                    EncyclopediaGUIConfig.Colors.TEXT_WHITE);

            for (int level = 0; level < 5; level++) {
                float rate = workPreferences[work][level];
                int lineY = workY + 5 + level * 9;

                String levelText = switch (level) {
                    case 0 -> "I";
                    case 1 -> "II";
                    case 2 -> "III";
                    case 3 -> "IV";
                    case 4 -> "V";
                    default -> "";
                };

                graphics.drawString(this.font, levelText,
                        workX + 25, lineY,
                        EncyclopediaGUIConfig.Colors.TEXT_GRAY);

                int barX = workX + 45;
                int barWidth = EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_BAR_WIDTH;
                int barFilled = (int)(barWidth * rate);

                // 绘制背景条（灰色）
                graphics.fill(barX, lineY + 2, barX + barWidth,
                        lineY + EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_BAR_HEIGHT,
                        0xFF333333);

                // 绘制填充条（根据成功率着色）
                if (barFilled > 0) {
                    graphics.fill(barX, lineY + 2, barX + barFilled,
                            lineY + EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_BAR_HEIGHT,
                            EncyclopediaGUIConfig.Colors.getWorkRateColor(rate));
                }

                String rateText;
                if (rate <= -0.01f) {  // 负数或 -1 都显示 "/"
                    rateText = "/";
                } else {
                    rateText = String.format("%.0f%%", rate * 100);
                }
                graphics.drawString(this.font, rateText,
                        barX + barWidth + 3, lineY,
                        rate <= -0.01f ? EncyclopediaGUIConfig.Colors.TEXT_GRAY : EncyclopediaGUIConfig.Colors.TEXT_WHITE);
            }
        }
    }

    private void renderRightColumn(GuiGraphics graphics, int mouseX, int mouseY, int startY) {
        int x = this.width - EncyclopediaGUIConfig.Layout.RightColumn.OFFSET_FROM_RIGHT;
        int y = startY + EncyclopediaGUIConfig.Layout.RightColumn.START_Y_OFFSET;

        // ========== 管理须知 ==========
        graphics.drawString(this.font, "管理须知",
                x, y,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 动态渲染所有管理须知
        for (int i = 0; i < manualsUnlocked.length; i++) {
            String manualTitle = "管理须知 " + toRoman(i + 1);
            String manualContent = i < manualTexts.length ? manualTexts[i] : "未知";
            renderTextOnlyEntry(graphics, x, y, manualTitle,
                    manualContent,
                    manualsUnlocked[i],
                    1,  // 管理须知固定需要等级1
                    manualCost,
                    mouseX, mouseY, i + 2);
            y += ENTRY_HEIGHT + ENTRY_SPACING;
        }

        // 工具类型异想体不显示后续内容
        if (isToolType) return;

        // ========== 敏感信息 ==========
        graphics.drawString(this.font, "异想体的敏感信息",
                x, y,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 逆卡巴拉计数极值
        renderTextOnlyEntry(graphics, x, y, "逆卡巴拉计数极值",
                sensitiveInfoText,
                sensitiveInfoUnlocked,
                EncyclopediaGUIConfig.UnlockCosts.SENSITIVE_INFO_LEVEL,
                sensitiveInfoCost,
                mouseX, mouseY, 1);

        y += ENTRY_HEIGHT + 25;

        // 获取异想体实体
        IAbnormality abnormality = this.abnormality;
        if (abnormality == null) {
            showErrorMessage("严重错误：找不到异想体！", 40);
            return;
        }

        // ========== E.G.O 饰品 ==========
        if (abnormality.getEGOGiftData() != null) {
            boolean giftUnlocked = canUnlockGift(abnormality, observationLevel);
            int giftRequiredLevel = getRequiredLevelForGift(abnormality);

            if (giftUnlocked) {
                // 已解锁：显示饰品和获取率
                float giftProb = abnormality.getGiftProbability() * 100;
                graphics.drawString(this.font,
                        String.format("E.G.O饰品（获得率%.1f%%）", giftProb),
                        x, y,
                        EncyclopediaGUIConfig.Colors.THEME_ORANGE);
                y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;
                renderEGOGift(graphics, x, y, abnormality);
                y += 85;
            } else {
                // 未解锁：显示需要的等级
                graphics.drawString(this.font,
                        String.format("E.G.O饰品（观察等级%d解锁）", giftRequiredLevel),
                        x, y, 0xFF888888);
                y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

                // 绘制锁定框
                graphics.fill(x, y, x + 70, y + 70, 0xFF1A1A1A);
                graphics.drawCenteredString(this.font, "未解锁", x + 35, y + 30, 0xFF666666);
                y += 85;
            }
        }

        // ========== E.G.O 护甲和武器 ==========
        boolean armorUnlocked = canUnlockArmor(abnormality, observationLevel);
        boolean weaponUnlocked = canUnlockWeapon(abnormality, observationLevel);
        int armorRequiredLevel = getRequiredLevelForArmor(abnormality);
        int weaponRequiredLevel = getRequiredLevelForWeapon(abnormality);

        // E.G.O 护甲（左侧）
        int armorX = x;
        if (abnormality.getEGOArmorData() != null) {
            if (armorUnlocked) {
                renderEGOArmor(graphics, armorX, y, abnormality);
            } else {
                // 未解锁提示
                graphics.drawCenteredString(this.font, "E.G.O护甲",
                        armorX + 75, y + 8, 0xFF888888);
                graphics.drawCenteredString(this.font,
                        String.format("观察等级%d解锁", armorRequiredLevel),
                        armorX + 75, y + 70, 0xFF666666);
                graphics.fill(armorX, y + 20, armorX + 150, y + 60, 0xFF1A1A1A);
            }
        }

        // E.G.O 武器（右侧）
        int weaponX = x + 165;
        if (abnormality.getEGOWeaponData() != null) {
            if (weaponUnlocked) {
                renderEGOWeapon(graphics, weaponX, y, abnormality);
            } else {
                // 未解锁提示
                graphics.drawCenteredString(this.font, "E.G.O武器",
                        weaponX + 75, y + 8, 0xFF888888);
                graphics.drawCenteredString(this.font,
                        String.format("观察等级%d解锁", weaponRequiredLevel),
                        weaponX + 75, y + 70, 0xFF666666);
                graphics.fill(weaponX, y + 20, weaponX + 150, y + 60, 0xFF1A1A1A);
            }
        }
    }

    /**
     * 获取解锁饰品所需的最低观察等级
     */
    private int getRequiredLevelForGift(IAbnormality abnormality) {
        if (abnormality == null) return 2;  // 默认等级2
        for (int i = 1; i <= 4; i++) {
            if (abnormality.getObservationBonus(i).unlocksGift()) {
                return i;
            }
        }
        return 2;  // 如果没有配置，默认等级2
    }

    /**
     * 获取解锁护甲所需的最低观察等级
     */
    private int getRequiredLevelForArmor(IAbnormality abnormality) {
        if (abnormality == null) return 4;  // 默认等级4
        for (int i = 1; i <= 4; i++) {
            if (abnormality.getObservationBonus(i).unlocksArmor()) {
                return i;
            }
        }
        return 4;  // 如果没有配置，默认等级4
    }

    /**
     * 获取解锁武器所需的最低观察等级
     */
    private int getRequiredLevelForWeapon(IAbnormality abnormality) {
        if (abnormality == null) return 4;  // 默认等级4
        for (int i = 1; i <= 4; i++) {
            if (abnormality.getObservationBonus(i).unlocksWeapon()) {
                return i;
            }
        }
        return 4;  // 如果没有配置，默认等级4
    }

    /**
     * 判断当前观察等级是否能解锁饰品
     */
    private boolean canUnlockGift(IAbnormality abnormality, int currentLevel) {
        if (abnormality == null) return false;
        for (int i = 1; i <= currentLevel; i++) {
            if (abnormality.getObservationBonus(i).unlocksGift()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前观察等级是否能解锁护甲
     */
    private boolean canUnlockArmor(IAbnormality abnormality, int currentLevel) {
        if (abnormality == null) return false;
        for (int i = 1; i <= currentLevel; i++) {
            if (abnormality.getObservationBonus(i).unlocksArmor()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前观察等级是否能解锁武器
     */
    private boolean canUnlockWeapon(IAbnormality abnormality, int currentLevel) {
        if (abnormality == null) return false;
        for (int i = 1; i <= currentLevel; i++) {
            if (abnormality.getObservationBonus(i).unlocksWeapon()) {
                return true;
            }
        }
        return false;
    }

    private void renderTextOnlyEntry(GuiGraphics graphics, int x, int y,
                                     String title, String content,
                                     boolean unlocked, int reqLevel, int cost,
                                     int mouseX, int mouseY, int entryIndex) {
        int width = EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_WIDTH;
        int height = ENTRY_HEIGHT;

        boolean isHovered = isMouseOver(mouseX, mouseY, x, y, width, height);

        drawDashedBorder(graphics, x, y, width, height);

        int bgColor = isHovered ? 0x80333333 : 0x80222222;
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, bgColor);

        graphics.drawString(this.font, title,
                x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                y + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_Y,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        if (unlocked) {
            drawWrappedText(graphics, content,
                    x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                    y + 22, width - 20,
                    EncyclopediaGUIConfig.Colors.STATUS_RED);

        } else {
            boolean canUnlock = observationLevel >= reqLevel;
            boolean hasEnergy = playerEnergy >= cost;

            if (canUnlock) {
                int costColor = hasEnergy ?
                        EncyclopediaGUIConfig.Colors.STATUS_GREEN :
                        EncyclopediaGUIConfig.Colors.STATUS_RED;
                graphics.drawString(this.font,
                        String.format(EncyclopediaGUIConfig.Text.UNLOCK_COST, cost),
                        x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                        y + EncyclopediaGUIConfig.Layout.RightColumn.UNLOCK_TEXT_OFFSET_Y,
                        costColor);

                if (isHovered && hasEnergy) {
                    graphics.drawString(this.font,
                            EncyclopediaGUIConfig.Text.UNLOCK_HINT,
                            x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                            y + EncyclopediaGUIConfig.Layout.RightColumn.CLICK_HINT_OFFSET_Y,
                            EncyclopediaGUIConfig.Colors.THEME_YELLOW);
                }

            } else {
                graphics.drawString(this.font,
                        String.format(EncyclopediaGUIConfig.Text.REQUIRE_LEVEL, reqLevel),
                        x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                        y + EncyclopediaGUIConfig.Layout.RightColumn.REQUIREMENT_OFFSET_Y,
                        EncyclopediaGUIConfig.Colors.TEXT_WARNING);
            }
        }
    }

    private void renderBottomBar(GuiGraphics graphics) {
        int y = this.height - EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT;

        graphics.fill(0, y, this.width, this.height,
                EncyclopediaGUIConfig.Colors.BACKGROUND_SEMI);

        graphics.drawString(this.font,
                String.format(EncyclopediaGUIConfig.Text.AVAILABLE_PEBOX, playerEnergy),
                EncyclopediaGUIConfig.Layout.BOTTOM_BAR_TEXT_X,
                y + EncyclopediaGUIConfig.Layout.BOTTOM_BAR_TEXT_Y,
                EncyclopediaGUIConfig.Colors.THEME_YELLOW);

        graphics.drawCenteredString(this.font,
                EncyclopediaGUIConfig.Text.CONTROLS_HINT,
                this.width / 2,
                y + EncyclopediaGUIConfig.Layout.BOTTOM_BAR_TEXT_Y,
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);
    }

    private void renderScrollbar(GuiGraphics graphics, int viewportTop, int viewportHeight, int mouseX, int mouseY) {
        int maxScroll = Math.max(0, EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT - viewportHeight);

        if (maxScroll <= 0) return; // 内容不需要滚动

        // 滚动条背景
        int barX = this.width - EncyclopediaGUIConfig.Layout.SCROLLBAR_OFFSET_X;
        graphics.fill(barX, viewportTop,
                barX + EncyclopediaGUIConfig.Layout.SCROLLBAR_WIDTH,
                viewportTop + viewportHeight,
                EncyclopediaGUIConfig.Colors.SCROLLBAR_BACKGROUND);

        // 滚动条滑块
        float scrollPercent = (float) scrollOffset / maxScroll;
        int barHeight = Math.max(
                EncyclopediaGUIConfig.Layout.SCROLLBAR_MIN_HEIGHT,
                (int)(viewportHeight * ((float)viewportHeight / EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT)));
        int barY = viewportTop + (int)((viewportHeight - barHeight) * scrollPercent);

        // 高亮滑块如果鼠标悬停或正在拖动
        boolean isHovered = isMouseOver(mouseX, mouseY, barX, barY,
                EncyclopediaGUIConfig.Layout.SCROLLBAR_WIDTH, barHeight);
        int handleColor = (isHovered || isDraggingScrollbar) ?
                0xFFFFDD00 : EncyclopediaGUIConfig.Colors.SCROLLBAR_HANDLE;

        graphics.fill(barX, barY,
                barX + EncyclopediaGUIConfig.Layout.SCROLLBAR_WIDTH,
                barY + barHeight,
                handleColor);
    }

    private void renderMessage(GuiGraphics graphics) {
        if (message == null || message.isEmpty()) return;
        // 计算位置（屏幕中间偏下）
        int centerX = this.width / 2;
        int logY = this.height * 2 / 3 + 60;

        // 计算背景框尺寸
        int textWidth = this.font.width(message);
        int padding = 16;
        int bgWidth = textWidth + padding * 2;
        int bgHeight = this.font.lineHeight + padding;
        int bgX = centerX - bgWidth / 2;

        float alpha;
        int visibleChars;
        visibleChars = message.length();
        alpha = 1.0f;

        // 启用混合
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绘制背景（灰色半透明）
        int bgAlpha = (int) (alpha * 180);  // 最大180透明度
        int bgColor = (bgAlpha << 24) | 0x2A2A2A;  // 深灰色
        graphics.fill(bgX, logY, bgX + bgWidth, logY + bgHeight, bgColor);

        // 绘制边框（浅一点的灰色）
        int borderAlpha = (int) (alpha * 220);
        int borderColor = (borderAlpha << 24) | 0x555555;
        // 上边框
        graphics.fill(bgX, logY, bgX + bgWidth, logY + 1, borderColor);
        // 下边框
        graphics.fill(bgX, logY + bgHeight - 1, bgX + bgWidth, logY + bgHeight, borderColor);
        // 左边框
        graphics.fill(bgX, logY, bgX + 1, logY + bgHeight, borderColor);
        // 右边框
        graphics.fill(bgX + bgWidth - 1, logY, bgX + bgWidth, logY + bgHeight, borderColor);

        // 绘制文本（带透明度）
        String displayText = message.substring(0, visibleChars);
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;  // 白色

        int textX = centerX - this.font.width(displayText) / 2;
        int textY = logY + padding / 2;

        graphics.drawString(this.font, displayText, textX, textY, textColor);

        // 恢复渲染状态
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ==================== 辅助方法 ====================

    private void drawDashedBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        int dashLength = EncyclopediaGUIConfig.Animation.DASH_LENGTH;
        int color = EncyclopediaGUIConfig.Colors.THEME_ORANGE;

        for (int i = 0; i < w; i += dashLength * 2) {
            graphics.fill(x + i, y, x + Math.min(i + dashLength, w), y + 2, color);
            graphics.fill(x + i, y + h - 2, x + Math.min(i + dashLength, w), y + h, color);
        }

        for (int i = 0; i < h; i += dashLength * 2) {
            graphics.fill(x, y + i, x + 2, y + Math.min(i + dashLength, h), color);
            graphics.fill(x + w - 2, y + i, x + w, y + Math.min(i + dashLength, h), color);
        }
    }

    private void drawWrappedText(GuiGraphics graphics, String text,
                                 int x, int y, int maxWidth, int color) {
        String[] chars = text.split("");
        StringBuilder line = new StringBuilder();
        int currentY = y;

        for (String ch : chars) {
            if (this.font.width(line.toString() + ch) > maxWidth) {
                graphics.drawString(this.font, line.toString(), x, currentY, color);
                currentY += 10;
                line = new StringBuilder(ch);
            } else {
                line.append(ch);
            }
        }

        if (line.length() > 0) {
            graphics.drawString(this.font, line.toString(), x, currentY, color);
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        mouseX = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
        mouseY = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
        int viewportHeight = this.height - EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT -
                EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT;
        int maxScroll = Math.max(0, EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT - viewportHeight);

        targetScrollOffset -= scrollDelta * EncyclopediaGUIConfig.Layout.SCROLL_SPEED;
        targetScrollOffset = Mth.clamp(targetScrollOffset, 0, maxScroll);

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
        mouseY = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 检查滚动条点击
        int viewportTop = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT;
        int viewportHeight = this.height - EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT -
                EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT;
        int maxScroll = Math.max(0, EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT - viewportHeight);

        if (maxScroll > 0) {
            int barX = this.width - EncyclopediaGUIConfig.Layout.SCROLLBAR_OFFSET_X;
            float scrollPercent = (float) scrollOffset / maxScroll;
            int barHeight = Math.max(
                    EncyclopediaGUIConfig.Layout.SCROLLBAR_MIN_HEIGHT,
                    (int)(viewportHeight * ((float)viewportHeight / EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT)));
            int barY = viewportTop + (int)((viewportHeight - barHeight) * scrollPercent);

            if (isMouseOver((int)mouseX, (int)mouseY, barX, barY,
                    EncyclopediaGUIConfig.Layout.SCROLLBAR_WIDTH, barHeight)) {
                isDraggingScrollbar = true;
                dragStartY = (int)mouseY;
                dragStartScroll = scrollOffset;
                return true;
            }
        }

        // 调整鼠标坐标以匹配滚动内容
        int adjustedMouseY = (int)(mouseY + scrollOffset);
        IAbnormality abnormality = this.abnormality;

        // ========== E.G.O 装备研发点击（仅在非工具类型时处理）==========
        if (abnormality != null && !isToolType) {
            // 计算右侧栏的基础坐标
            int startY = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT + 5;
            int x = this.width - EncyclopediaGUIConfig.Layout.RightColumn.OFFSET_FROM_RIGHT;
            int y = startY + EncyclopediaGUIConfig.Layout.RightColumn.START_Y_OFFSET;

            // 跳过管理须知部分
            y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;
            for (int i = 0; i < manualsUnlocked.length; i++) {
                y += ENTRY_HEIGHT + ENTRY_SPACING;
            }

            // 跳过敏感信息部分
            y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;
            y += ENTRY_HEIGHT + 25;

            // 跳过E.G.O饰品部分（如果有）
            if (abnormality.getEGOGiftData() != null) {
                y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING + 85;
            }

            // E.G.O护甲的研发区域（动态判断解锁状态）
            boolean armorUnlocked = canUnlockArmor(abnormality, observationLevel);
            if (armorUnlocked && abnormality.getEGOArmorData() != null) {
                int armorX = x;
                int armorWidth = 150;
                int armorHeight = 230;

                // "研发"整行区域
                int devBarX = armorX + 5;
                int devBarY = y + armorHeight - 20;
                int devBarWidth = armorWidth - 10;
                int devBarHeight = 15;

                if (isMouseOver((int) mouseX, adjustedMouseY, devBarX, devBarY, devBarWidth, devBarHeight)) {
                    onArmorDevelopmentClicked(abnormality);
                    return true;
                }
            }

            // E.G.O武器的研发区域（动态判断解锁状态）
            boolean weaponUnlocked = canUnlockWeapon(abnormality, observationLevel);
            if (weaponUnlocked && abnormality.getEGOWeaponData() != null) {
                int weaponX = x + 165;
                int weaponWidth = 150;
                int weaponHeight = 230;

                // "研发"整行区域
                int devBarX = weaponX + 5;
                int devBarY = y + weaponHeight - 20;
                int devBarWidth = weaponWidth - 10;
                int devBarHeight = 15;

                if (isMouseOver((int) mouseX, adjustedMouseY, devBarX, devBarY, devBarWidth, devBarHeight)) {
                    onWeaponDevelopmentClicked(abnormality);
                    return true;
                }
            }
        }

        // ========== 基础信息、工作偏好、管理须知、敏感信息的解锁点击 ==========
        int centerX = EncyclopediaGUIConfig.Layout.CenterColumn.X;
        int imgY = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT + 20;
        int imgSize = EncyclopediaGUIConfig.Layout.CenterColumn.IMAGE_SIZE;

        // 基础信息解锁（图片 + 详情）
        if (!basicInfoUnlocked) {
            // 1. 异想体图片区域
            if (isMouseOver((int)mouseX, adjustedMouseY, centerX, imgY, imgSize, imgSize)) {
                if (playerEnergy < basicInfoCost) {
                    showErrorMessage("§cPE-BOX不足！需要 " + basicInfoCost + " 个，当前只有 " + playerEnergy + " 个", 30);
                    return true;
                }
                unlockEntry(0, basicInfoCost);
                return true;
            }

            // 2. 基础信息详情区域
            int infoY = imgY + imgSize + EncyclopediaGUIConfig.Layout.CenterColumn.BASIC_INFO_Y_OFFSET - imgSize;
            int basicInfoHeight = 120;
            int basicInfoWidth = 180;

            if (isMouseOver((int)mouseX, adjustedMouseY, centerX, infoY, basicInfoWidth, basicInfoHeight)) {
                if (playerEnergy < basicInfoCost) {
                    showErrorMessage("§cPE-BOX不足！需要 " + basicInfoCost + " 个，当前只有 " + playerEnergy + " 个", 30);
                    return true;
                }
                unlockEntry(0, basicInfoCost);
                return true;
            }
        }

        // 工作偏好解锁（仅非工具类型）
        if (!isToolType) {
            int infoY = imgY + imgSize + EncyclopediaGUIConfig.Layout.CenterColumn.BASIC_INFO_Y_OFFSET - imgSize;
            int workPrefY = infoY + EncyclopediaGUIConfig.Layout.CenterColumn.WORK_PREF_Y_OFFSET - 240;

            // 检查4个工作类型的点击
            for (int work = 0; work < 4; work++) {
                if (!workPreferencesUnlocked[work]) {
                    int col = work % 2;
                    int row = work / 2;

                    int workX = centerX + col * 180;
                    int workY = workPrefY + EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING + row * 90;
                    int lockWidth = 170;
                    int lockHeight = 70;

                    if (isMouseOver((int)mouseX, adjustedMouseY, workX, workY, lockWidth, lockHeight)) {
                        if (playerEnergy < workPreferencesCost) {
                            showErrorMessage("§cPE-BOX不足！需要 " + workPreferencesCost + " 个，当前只有 " + playerEnergy + " 个", 30);
                            return true;
                        }
                        // entryIndex: 基础信息=0, 敏感信息=2, 管理须知=3+
                        // 工作偏好使用 100+work (100, 101, 102, 103)
                        unlockEntry(100 + work, workPreferencesCost);
                        return true;
                    }
                }
            }
        }

        // 右侧栏：管理须知和敏感信息
        int startY = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT + 5;
        int x = this.width - EncyclopediaGUIConfig.Layout.RightColumn.OFFSET_FROM_RIGHT;
        int y = startY + EncyclopediaGUIConfig.Layout.RightColumn.START_Y_OFFSET;

        // 跳过"管理须知"标题
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 检查所有管理须知解锁点击
        for (int i = 0; i < manualsUnlocked.length; i++) {
            if (!manualsUnlocked[i]) {
                if (isMouseOver((int)mouseX, adjustedMouseY, x, y,
                        EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_WIDTH, ENTRY_HEIGHT)) {
                    int reqLevel = 1;

                    if (observationLevel < reqLevel) {
                        showErrorMessage("§c需要观察等级 " + reqLevel + "，当前等级 " + observationLevel, 40);
                        return true;
                    }
                    if (playerEnergy < manualCost) {
                        showErrorMessage("§cPE-BOX不足！需要 " + manualCost + " 个，当前只有 " + playerEnergy + " 个", 30);
                        return true;
                    }

                    unlockEntry(i + 3, manualCost);
                    return true;
                }
            }
            y += ENTRY_HEIGHT + ENTRY_SPACING;
        }

        // 跳过"异想体的敏感信息"标题
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 检查敏感信息解锁点击（仅非工具类型）
        if (!isToolType && !sensitiveInfoUnlocked) {
            if (isMouseOver((int)mouseX, adjustedMouseY, x, y,
                    EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_WIDTH, ENTRY_HEIGHT)) {
                int reqLevel = EncyclopediaGUIConfig.UnlockCosts.SENSITIVE_INFO_LEVEL;

                if (observationLevel < reqLevel) {
                    showErrorMessage("§c需要观察等级 " + reqLevel + "，当前等级 " + observationLevel, 40);
                    return true;
                }
                if (playerEnergy < sensitiveInfoCost) {
                    showErrorMessage("§cPE-BOX不足！需要 " + sensitiveInfoCost + " 个，当前只有 " + playerEnergy + " 个", 30);
                    return true;
                }

                unlockEntry(2, sensitiveInfoCost);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
        mouseY = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
        dragX = dragX / Math.max(uiScale, 0.0001f);
        dragY = dragY / Math.max(uiScale, 0.0001f);
        if (isDraggingScrollbar && button == 0) {
            int viewportTop = EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT;
            int viewportHeight = this.height - EncyclopediaGUIConfig.Layout.TOP_BAR_HEIGHT -
                    EncyclopediaGUIConfig.Layout.BOTTOM_BAR_HEIGHT;
            int maxScroll = Math.max(0, EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT - viewportHeight);

            int barHeight = Math.max(
                    EncyclopediaGUIConfig.Layout.SCROLLBAR_MIN_HEIGHT,
                    (int)(viewportHeight * ((float)viewportHeight / EncyclopediaGUIConfig.Layout.CONTENT_HEIGHT)));

            int deltaY = (int)mouseY - dragStartY;
            double scrollRange = viewportHeight - barHeight;
            double scrollDelta = (deltaY / scrollRange) * maxScroll;

            targetScrollOffset = Mth.clamp(dragStartScroll + scrollDelta, 0, maxScroll);
            scrollOffset = targetScrollOffset; // 直接设置，不平滑

            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
        mouseY = com.wzz.lobotocraft.client.screen.api.AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void onWeaponDevelopmentClicked(IAbnormality abnormality) {
        if (developmentWeaponCount >= abnormality.getWeaponDevelopmentMaxCount()) {
            showErrorMessage("§c研发武器失败！已达到最大研发上限", 30);
            return;
        }
        if (abnormality.getWeaponDevelopmentCost() == -1) {
            showErrorMessage("§c研发武器失败，无法研究！", 30);
            return;
        }
        if (playerEnergy < abnormality.getWeaponDevelopmentCost()) {
            showErrorMessage("§c研发武器失败，PE-BOX不足，需要 " + abnormality.getWeaponDevelopmentCost() + " 个PE-BOX！", 30);
            return;
        }

        // 打开制造界面
        if (minecraft != null) {
            minecraft.setScreen(new EquipmentCraftingScreen(
                    "weapon",
                    abnormalityCode,
                    abnormalityId,
                    targetScrollOffset,
                    abnormality,
                    abnormality.getWeaponDevelopmentCost()
            ));
        }
    }

    private void onArmorDevelopmentClicked(IAbnormality abnormality) {
        if (developmentArmorCount >= abnormality.getArmorDevelopmentMaxCount()) {
            showErrorMessage("§c研发装备失败！已达到最大研发上限", 30);
            return;
        }
        if (abnormality.getArmorDevelopmentCost() == -1) {
            showErrorMessage("§c研发装备失败，无法研究！", 30);
            return;
        }
        if (playerEnergy < abnormality.getArmorDevelopmentCost()) {
            showErrorMessage("§c研发装备失败，PE-BOX不足，需要 " + abnormality.getArmorDevelopmentCost() + " 个PE-BOX！", 30);
            return;
        }

        // 打开制造界面
        if (minecraft != null) {
            minecraft.setScreen(new EquipmentCraftingScreen(
                    "armor",
                    abnormalityCode,
                    abnormalityId,
                    targetScrollOffset,
                    abnormality,
                    abnormality.getArmorDevelopmentCost()
            ));
        }
    }

    private void unlockEntry(int entryIndex, int cost) {
        // 发送解锁请求到服务器
        MessageLoader.getLoader().sendToServer(
                new UnlockManualPacket(abnormalityCode, entryIndex, cost)
        );

        // 本地更新解锁状态
        playerEnergy -= cost;
        if (entryIndex == 0) {
            basicInfoUnlocked = true;
        } else if (entryIndex >= 100 && entryIndex <= 103) {
            // 工作偏好解锁 (100=本能, 101=洞察, 102=依恋, 103=压迫)
            int workIndex = entryIndex - 100;
            workPreferencesUnlocked[workIndex] = true;
        } else if (entryIndex == 2) {
            sensitiveInfoUnlocked = true;
        } else if (entryIndex >= 3) {
            // 管理须知解锁 (index >= 3)
            int manualIndex = entryIndex - 3;
            if (manualIndex >= 0 && manualIndex < manualsUnlocked.length) {
                manualsUnlocked[manualIndex] = true;
            }
        }

        // 规则：基础信息(+1) + 每个工作偏好(+0.25) + 敏感信息(+1) + 管理须知全部解锁(+1)
        observationLevel = 0;
        if (basicInfoUnlocked) observationLevel++;

        // 计算工作偏好解锁数量
        int unlockedWorkCount = 0;
        for (boolean unlocked : workPreferencesUnlocked) {
            if (unlocked) unlockedWorkCount++;
        }
        // 每解锁一个工作类型增加0.25等级，全部解锁为1级
        if (unlockedWorkCount == 4) {
            observationLevel++;
        }

        if (sensitiveInfoUnlocked) observationLevel++;

        // 检查管理须知是否全部解锁
        boolean allManualsUnlocked = true;
        for (boolean unlocked : manualsUnlocked) {
            if (!unlocked) {
                allManualsUnlocked = false;
                break;
            }
        }
        if (allManualsUnlocked && manualsUnlocked.length > 0) {
            observationLevel++;
        }

        // 播放音效
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ModSounds.UNLOCK_INFORMATION.get(), 1.0F, 1.5F);
        }
    }

    /**
     * 将数字转换为罗马数字
     */
    private String toRoman(int num) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (num > 0 && num <= romans.length) {
            return romans[num - 1];
        }
        return String.valueOf(num);
    }

    private void showErrorMessage(String message, int maxShowTick) {
        if (minecraft != null && minecraft.player != null) {
            this.message = message;
            this.maxShowTick = maxShowTick;
            minecraft.player.playSound(ModSounds.ENERGY_SHORTAGE_INFORMATION.get(), 1.0F, 1.0F);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
