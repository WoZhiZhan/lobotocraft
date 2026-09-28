package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.screen.api.AbnormalityMusicScreen;
import com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig;
import com.wzz.lobotocraft.client.ui.DamageTypeVisual;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.UnlockManualPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_HEIGHT;
import static com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig.Layout.RightColumn.ENTRY_SPACING;

public class AbnormalityEncyclopediaScreen extends AbnormalityMusicScreen {

    private record SensitiveResistance(String type, float value) {
    }

    private static final int CANVAS_W = 1920;
    private static final int CANVAS_H = 1080;
    private static final int PANEL_BLACK = 0xF2070907;
    private static final int PANEL_INNER = 0xD90C100C;
    private static final int ARCHIVE_GOLD = 0xFFC98A36;
    private static final int ARCHIVE_GOLD_DIM = 0xFF72552C;
    private static final int ARCHIVE_PALE = 0xFFE7E3A1;
    private static final int ARCHIVE_DIM = 0xFF88876B;
    private static final int ARCHIVE_RED = 0xFFFF3D43;

    private static final int BASIC_X = 430;
    private static final int BASIC_Y = 210;
    private static final int BASIC_W = 390;
    private static final int BASIC_H = 830;
    private static final int MANUAL_X = 840;
    private static final int MANUAL_Y = 34;
    private static final int MANUAL_W = 470;
    private static final int MANUAL_H = 548;
    private static final int MANUAL_VIEW_X = 856;
    private static final int MANUAL_VIEW_Y = 92;
    private static final int MANUAL_VIEW_W = 438;
    private static final int MANUAL_VIEW_H = 470;
    private static final int WORK_X = 840;
    private static final int WORK_Y = 602;
    private static final int WORK_W = 470;
    private static final int WORK_H = 438;
    private static final int SENSITIVE_X = 1330;
    private static final int SENSITIVE_Y = 42;
    private static final int SENSITIVE_W = 540;
    private static final int SENSITIVE_H = 196;
    private static final int GIFT_X = 1330;
    private static final int GIFT_Y = 254;
    private static final int GIFT_W = 540;
    private static final int GIFT_H = 158;
    private static final int EGO_Y = 422;
    private static final int EGO_W = 260;
    private static final int EGO_H = 472;
    private static final int WEAPON_X = 1330;
    private static final int ARMOR_X = 1610;
    private static final int EXIT_X = 1700;
    private static final int EXIT_Y = 986;
    private static final int EXIT_W = 200;
    private static final int EXIT_H = 76;
    private static final int HEADER_INFO_TAB_X = 72;
    private static final int HEADER_STORY_TAB_X = 382;
    private static final int HEADER_TAB_Y = 112;
    private static final int HEADER_TAB_W = 286;
    private static final int HEADER_TAB_H = 56;
    private static final int STORY_VIEW_X = 510;
    private static final int STORY_VIEW_Y = 278;
    private static final int STORY_VIEW_W = 1328;
    private static final int STORY_VIEW_H = 700;

    private static final ResourceLocation INSTINCT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_instinct.png");
    private static final ResourceLocation INSIGHT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_insight.png");
    private static final ResourceLocation ATTACHMENT_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_attachment.png");
    private static final ResourceLocation REPRESSION_ICON =
            ResourceUtil.createInstance("textures/gui/work_pref_repression.png");

    private static final ResourceLocation QUESTION_MARK =
            ResourceUtil.createInstance("textures/gui/question_mark.png");
    private static final Pattern SENSITIVE_RESISTANCE_PATTERN = Pattern.compile(
            "(红色|白色|黑色|蓝色|苍白|RED|WHITE|BLACK|PALE|BLUE)\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern SENSITIVE_RESISTANCE_CLAUSE_PATTERN = Pattern.compile(
            "(?:红色|白色|黑色|蓝色|苍白|RED|WHITE|BLACK|PALE|BLUE)\\s*\\([^)]*\\)");

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
    private final AbnormalityEncyclopediaData.StoryEntry[] storyEntries;
    private final String sensitiveInfoText;
    private final Screen returnScreen;
    private boolean showingStories;

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

    // 管理须知面板的独立滚动状态。
    private double scrollOffset;
    private double targetScrollOffset;

    // 滚动条拖动相关
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private double dragStartScroll = 0;

    private int realMouseX;
    private int realMouseY;

    private float canvasScale = 1.0F;
    private float canvasX;
    private float canvasY;

    private String message;
    private int showTick;
    private int maxShowTick = 40;

    public AbnormalityEncyclopediaScreen(int abnormalityId, String code, String name,
                                         RiskLevel riskLevel, String damageType, int maxPEOutput,
                                         float[][] workPreferences, int observationLevel,
                                         String[] manualTexts,
                                         AbnormalityEncyclopediaData.StoryEntry[] storyTexts,
                                         String sensitiveInfoText, Screen returnScreen,
                                         boolean basicInfo, boolean[] workPrefsUnlocked, boolean sensitiveInfo,
                                         boolean[] manualsUnlocked,
                                         int basicInfoCost, int workPreferencesCost,
                                         int sensitiveInfoCost, int manualCost, int developmentWeaponCount, int developmentArmorCount, double scrollOffset, boolean isToolType) {
        super(Component.translatable("screen.lobotocraft.encyclopedia.title"), abnormalityId);
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
        this.storyEntries = storyTexts == null
                ? new AbnormalityEncyclopediaData.StoryEntry[0] : storyTexts;
        this.sensitiveInfoText = sensitiveInfoText;
        this.returnScreen = returnScreen;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, EncyclopediaGUIConfig.Colors.BACKGROUND_BLACK);
        updateCanvasTransform();
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);
        realMouseX = Mth.floor(logicalMouseX);
        realMouseY = Mth.floor(logicalMouseY);

        int maxScroll = manualMaxScroll();
        targetScrollOffset = Mth.clamp(targetScrollOffset, 0.0D, maxScroll);
        scrollOffset += (targetScrollOffset - scrollOffset)
                * EncyclopediaGUIConfig.Animation.SCROLL_SMOOTHNESS;
        scrollOffset = Mth.clamp(scrollOffset, 0.0D, maxScroll);

        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0.0F);
        graphics.pose().scale(canvasScale, canvasScale, 1.0F);
        renderArchiveLayout(graphics, logicalMouseX, logicalMouseY);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateCanvasTransform() {
        canvasScale = Math.min((float) width / CANVAS_W, (float) height / CANVAS_H);
        canvasX = (width - CANVAS_W * canvasScale) * 0.5F;
        canvasY = (height - CANVAS_H * canvasScale) * 0.5F;
    }

    private void renderArchiveLayout(GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.fill(0, 0, CANVAS_W, CANVAS_H, 0xFF010302);
        renderArchiveHeader(graphics, mouseX, mouseY);
        if (showingStories) {
            renderStoryPage(graphics, mouseX, mouseY);
            renderExitButton(graphics, mouseX, mouseY);
            renderArchiveMessage(graphics);
            return;
        }
        renderObservationPanel(graphics);
        renderBasicPanel(graphics, mouseX, mouseY);
        renderManualPanel(graphics, mouseX, mouseY);
        renderWorkPreferencePanel(graphics, mouseX, mouseY);
        renderSensitivePanel(graphics, mouseX, mouseY);
        renderGiftPanel(graphics);
        renderEgoPanels(graphics);
        renderPeStorage(graphics);
        renderExitButton(graphics, mouseX, mouseY);
        renderArchiveMessage(graphics);
    }

    private void renderArchiveHeader(GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.fill(0, 0, 820, 8, ARCHIVE_RED);
        graphics.fill(0, 8, 620, 92, 0xEFFF363D);
        for (int row = 0; row < 92; row++) {
            int right = 620 + row * 2;
            graphics.fill(620, 8 + row, right, 9 + row, 0xEFFF363D);
        }
        drawScaledText(graphics, tr("screen.lobotocraft.encyclopedia.heading"),
                32, 23, 5.0F, 0xFF080A08, 540);
        renderHeaderArchiveTab(graphics, HEADER_INFO_TAB_X,
                tr("screen.lobotocraft.encyclopedia.tab.information"),
                !showingStories, mouseX, mouseY);
        renderHeaderArchiveTab(graphics, HEADER_STORY_TAB_X,
                tr("screen.lobotocraft.encyclopedia.tab.story"),
                showingStories, mouseX, mouseY);
        drawRightAlignedScaledText(graphics,
                abnormalityCode,
                818, 128, 2.0F, ARCHIVE_PALE, 160);
        graphics.fill(28, 188, 812, 190, ARCHIVE_RED);
    }

    private void renderHeaderArchiveTab(GuiGraphics graphics, int x, String label,
                                        boolean selected, double mouseX, double mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, HEADER_TAB_Y, HEADER_TAB_W, HEADER_TAB_H);
        int color = selected ? ARCHIVE_PALE : hovered ? ARCHIVE_GOLD : ARCHIVE_DIM;
        graphics.fill(x, HEADER_TAB_Y, x + HEADER_TAB_W, HEADER_TAB_Y + HEADER_TAB_H,
                selected ? 0xD91C2118 : 0xB9070907);
        graphics.fill(x, HEADER_TAB_Y + HEADER_TAB_H - 3,
                x + HEADER_TAB_W, HEADER_TAB_Y + HEADER_TAB_H, color);
        graphics.fill(x + 14, HEADER_TAB_Y + 13, x + 42, HEADER_TAB_Y + 41,
                selected ? ARCHIVE_GOLD : ARCHIVE_GOLD_DIM);
        drawScaledText(graphics, label, x + 58, HEADER_TAB_Y + 14,
                2.5F, color, HEADER_TAB_W - 72);
    }

    private void renderStoryPage(GuiGraphics graphics, double mouseX, double mouseY) {
        int portraitX = 34;
        int portraitY = 220;
        int portraitW = 438;
        int portraitH = 758;
        drawArchivePanel(graphics, portraitX, portraitY, portraitW, portraitH, ARCHIVE_PALE);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.archive"),
                portraitX, portraitY, portraitW);
        int imageX = portraitX + 28;
        int imageY = portraitY + 76;
        int imageSize = portraitW - 56;
        graphics.fill(imageX - 2, imageY - 2, imageX + imageSize + 2,
                imageY + imageSize + 2, ARCHIVE_GOLD_DIM);
        if (basicInfoUnlocked) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(abnormalityImage, imageX, imageY, 0, 0,
                    imageSize, imageSize, imageSize, imageSize);
        } else {
            graphics.fill(imageX, imageY, imageX + imageSize, imageY + imageSize,
                    0xEC050706);
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.basic_locked"),
                    portraitX + portraitW / 2, imageY + imageSize / 2 - 12,
                    2.0F, ARCHIVE_DIM, imageSize - 40);
        }
        int nameY = imageY + imageSize + 30;
        drawCenteredScaledText(graphics, basicInfoUnlocked ? abnormalityName : "???",
                portraitX + portraitW / 2, nameY, 3.0F, ARCHIVE_PALE, portraitW - 50);
        drawCenteredScaledText(graphics, abnormalityCode + " / " + riskLevel.name(),
                portraitX + portraitW / 2, nameY + 50, 2.0F,
                riskLevel.getColor(), portraitW - 50);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.observation_level_value",
                        observationLevel),
                portraitX + portraitW / 2, nameY + 102, 2.5F,
                ARCHIVE_GOLD, portraitW - 50);

        int panelX = 492;
        int panelY = 220;
        int panelW = 1378;
        int panelH = 758;
        drawArchivePanel(graphics, panelX, panelY, panelW, panelH, ARCHIVE_GOLD);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.story"), panelX, panelY, panelW);
        graphics.fill(STORY_VIEW_X, STORY_VIEW_Y,
                STORY_VIEW_X + STORY_VIEW_W, STORY_VIEW_Y + STORY_VIEW_H, 0xC9050705);
        enableLogicalScissor(graphics, STORY_VIEW_X, STORY_VIEW_Y,
                STORY_VIEW_W, STORY_VIEW_H);
        int y = STORY_VIEW_Y + 14 - Mth.floor(scrollOffset);
        if (storyEntries.length == 0) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.story.empty"),
                    STORY_VIEW_X + STORY_VIEW_W / 2, STORY_VIEW_Y + STORY_VIEW_H / 2,
                    2.5F, ARCHIVE_DIM, STORY_VIEW_W - 80);
        }
        for (int index = 0; index < storyEntries.length; index++) {
            int height = storyEntryHeight(index);
            if (y + height >= STORY_VIEW_Y && y <= STORY_VIEW_Y + STORY_VIEW_H) {
                renderOriginalStoryEntry(graphics, index, y, height);
            }
            y += height + 18;
        }
        graphics.disableScissor();
        renderStoryScrollbar(graphics, mouseX, mouseY);
    }

    private void renderOriginalStoryEntry(GuiGraphics graphics, int index, int y, int height) {
        AbnormalityEncyclopediaData.StoryEntry entry = storyEntries[index];
        int x = STORY_VIEW_X + 18;
        int width = STORY_VIEW_W - 50;
        boolean unlocked = entry.isUnlockedAt(observationLevel);
        drawArchivePanel(graphics, x, y, width, height,
                unlocked ? ARCHIVE_GOLD_DIM : 0xFF3D3E35);
        drawScaledText(graphics, tr("screen.lobotocraft.encyclopedia.archive_entry",
                        toRoman(index + 1)), x + 20, y + 14,
                2.0F, unlocked ? ARCHIVE_GOLD : ARCHIVE_DIM, 180);
        drawRightAlignedScaledText(graphics, tr("screen.lobotocraft.encyclopedia.observation_level_value",
                        entry.openLevel()),
                x + width - 20, y + 14, 2.0F,
                unlocked ? ARCHIVE_DIM : ARCHIVE_RED, 260);
        graphics.fill(x + 18, y + 43, x + width - 18, y + 44,
                unlocked ? ARCHIVE_GOLD_DIM : 0xFF30312B);
        if (unlocked) {
            drawWrappedScaledText(graphics, entry.text(), x + 22, y + 58,
                    2.0F, ARCHIVE_PALE, width - 44, Integer.MAX_VALUE);
        } else {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.encyclopedia.archive_locked"),
                    x + width / 2, y + 71, 2.0F, ARCHIVE_DIM, width - 80);
        }
    }

    private void renderStoryScrollbar(GuiGraphics graphics, double mouseX, double mouseY) {
        int maximum = manualMaxScroll();
        if (maximum <= 0) return;
        int trackX = STORY_VIEW_X + STORY_VIEW_W - 10;
        int handleHeight = manualScrollbarHeight();
        int handleY = STORY_VIEW_Y + Mth.floor((STORY_VIEW_H - handleHeight)
                * (scrollOffset / maximum));
        graphics.fill(trackX, STORY_VIEW_Y, trackX + 6,
                STORY_VIEW_Y + STORY_VIEW_H, 0x663E3D2B);
        int color = isDraggingScrollbar || inside(mouseX, mouseY, trackX - 4,
                handleY, 14, handleHeight) ? ARCHIVE_PALE : ARCHIVE_GOLD;
        graphics.fill(trackX, handleY, trackX + 6, handleY + handleHeight, color);
    }

    private void renderObservationPanel(GuiGraphics graphics) {
        int x = 26;
        int y = 218;
        int w = 374;
        int h = 776;
        drawArchivePanel(graphics, x, y, w, h, ARCHIVE_PALE);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.observation_bonuses"), x, y, w);

        IAbnormality current = abnormality;
        String[] bonuses = current == null ? new String[0] : current.getObservationBonusTexts();
        if (isToolType) {
            drawWrappedScaledText(graphics, tr("screen.lobotocraft.encyclopedia.tool_no_observation"),
                    x + 24, y + 78, 2.0F, ARCHIVE_DIM, w - 48, 5);
        } else {
            for (int level = 0; level < 4; level++) {
                int lineY = y + 78 + level * 112;
                int color = level < observationLevel ? ARCHIVE_PALE : 0xFF4A4B3D;
                drawScaledText(graphics, toRoman(level + 1), x + 24, lineY,
                        3.0F, color, 46);
                String bonus = level < bonuses.length
                        ? cleanObservationBonus(bonuses[level])
                        : tr("screen.lobotocraft.encyclopedia.observation_unconfigured");
                drawWrappedScaledText(graphics, bonus, x + 82, lineY + 4,
                        2.0F, color, w - 108, 3);
                graphics.fill(x + 22, lineY + 84, x + w - 22, lineY + 85,
                        level < observationLevel ? ARCHIVE_GOLD_DIM : 0xFF24261F);
            }
        }

        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.observation_level"),
                x + w / 2, y + h - 176,
                3.0F, ARCHIVE_GOLD, w - 40);
        drawCenteredScaledText(graphics, String.valueOf(observationLevel),
                x + w / 2, y + h - 110, 6.0F, ARCHIVE_PALE, 160);
    }

    private void renderBasicPanel(GuiGraphics graphics, double mouseX, double mouseY) {
        drawArchivePanel(graphics, BASIC_X, BASIC_Y, BASIC_W, BASIC_H,
                basicInfoUnlocked ? ARCHIVE_PALE : ARCHIVE_GOLD_DIM);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.basic_info"),
                BASIC_X, BASIC_Y, BASIC_W);

        int imageX = BASIC_X + 25;
        int imageY = BASIC_Y + 60;
        int imageSize = BASIC_W - 50;
        graphics.fill(imageX - 2, imageY - 2, imageX + imageSize + 2,
                imageY + imageSize + 2, ARCHIVE_GOLD_DIM);
        graphics.fill(imageX, imageY, imageX + imageSize, imageY + imageSize, 0xFF050706);
        if (basicInfoUnlocked) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(abnormalityImage, imageX, imageY, 0, 0,
                    imageSize, imageSize, imageSize, imageSize);
        } else {
            renderLockedArea(graphics, imageX, imageY, imageSize, imageSize,
                    basicInfoCost, 0, mouseX, mouseY);
        }

        int nameY = imageY + imageSize + 6;
        graphics.fill(imageX, nameY, imageX + imageSize, nameY + 72, 0xE72A2B1D);
        drawCenteredScaledText(graphics, basicInfoUnlocked ? abnormalityName : "???",
                BASIC_X + BASIC_W / 2, nameY + 8, 3.0F,
                basicInfoUnlocked ? ARCHIVE_PALE : ARCHIVE_DIM, imageSize - 20);
        drawScaledText(graphics, abnormalityCode, imageX + 14, nameY + 43,
                2.0F, ARCHIVE_PALE, 180);
        drawRightAlignedScaledText(graphics, riskLevel.name(), imageX + imageSize - 14,
                nameY + 38, 3.0F, riskLevel.getColor(), 150);

        int infoY = nameY + 92;
        if (!basicInfoUnlocked) {
            renderLockedArea(graphics, imageX, infoY, imageSize, 245,
                    basicInfoCost, 0, mouseX, mouseY);
            return;
        }

        drawArchivePanel(graphics, imageX, infoY, 160, 120, ARCHIVE_GOLD_DIM);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.damage_type"),
                imageX + 80, infoY + 9,
                2.0F, ARCHIVE_PALE, 145);
        renderDamageTypeIcon(graphics, damageType, imageX + 18, infoY + 43, 42);
        drawScaledText(graphics, DamageTypeVisual.label(damageType), imageX + 72, infoY + 53,
                3.0F, EncyclopediaGUIConfig.Colors.getDamageTypeColor(damageType), 78);

        drawArchivePanel(graphics, imageX + 180, infoY, 160, 120, ARCHIVE_GOLD_DIM);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.max_output"),
                imageX + 260, infoY + 9,
                2.0F, ARCHIVE_PALE, 145);
        drawCenteredScaledText(graphics, maxPEOutput < 0 ? "/" : String.valueOf(maxPEOutput),
                imageX + 260, infoY + 45, 5.0F, ARCHIVE_PALE, 120);

        int rangeY = infoY + 138;
        drawArchivePanel(graphics, imageX, rangeY, imageSize, 122, ARCHIVE_GOLD_DIM);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.work_result_output"),
                BASIC_X + BASIC_W / 2,
                rangeY + 8, 2.0F, ARCHIVE_PALE, imageSize - 20);
        if (!isToolType && maxPEOutput >= 0) {
            int goodMin = getDisplayedGoodWorkResultMin();
            int normalMin = getDisplayedNormalWorkResultMin();
            drawScaledText(graphics, tr("work_result.lobotocraft.good"), imageX + 18, rangeY + 43,
                    3.0F, EncyclopediaGUIConfig.Colors.STATUS_GREEN, 40);
            drawRightAlignedScaledText(graphics, goodMin > maxPEOutput ? "/" : goodMin + " - " + maxPEOutput,
                    imageX + imageSize - 16, rangeY + 46, 2.0F,
                    EncyclopediaGUIConfig.Colors.STATUS_GREEN, 160);
            drawScaledText(graphics, tr("work_result.lobotocraft.normal"), imageX + 18, rangeY + 70,
                    3.0F, EncyclopediaGUIConfig.Colors.STATUS_YELLOW, 40);
            drawRightAlignedScaledText(graphics, normalMin + " - " + Math.min(maxPEOutput, goodMin - 1),
                    imageX + imageSize - 16, rangeY + 73, 2.0F,
                    EncyclopediaGUIConfig.Colors.STATUS_YELLOW, 160);
            drawScaledText(graphics, tr("work_result.lobotocraft.bad"), imageX + 18, rangeY + 97,
                    3.0F, EncyclopediaGUIConfig.Colors.STATUS_RED, 40);
            drawRightAlignedScaledText(graphics, "0 - " + (normalMin - 1),
                    imageX + imageSize - 16, rangeY + 100, 2.0F,
                    EncyclopediaGUIConfig.Colors.STATUS_RED, 160);
        } else {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.tool_no_output"),
                    BASIC_X + BASIC_W / 2, rangeY + 62, 2.0F, ARCHIVE_DIM, imageSize - 30);
        }
    }

    private void renderManualPanel(GuiGraphics graphics, double mouseX, double mouseY) {
        drawArchivePanel(graphics, MANUAL_X, MANUAL_Y, MANUAL_W, MANUAL_H, ARCHIVE_GOLD);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.management_guidelines"),
                MANUAL_X, MANUAL_Y, MANUAL_W);
        graphics.fill(MANUAL_VIEW_X, MANUAL_VIEW_Y,
                MANUAL_VIEW_X + MANUAL_VIEW_W, MANUAL_VIEW_Y + MANUAL_VIEW_H, 0xC9050705);

        enableLogicalScissor(graphics, MANUAL_VIEW_X, MANUAL_VIEW_Y,
                MANUAL_VIEW_W, MANUAL_VIEW_H);
        int itemY = MANUAL_VIEW_Y + 8 - Mth.floor(scrollOffset);
        for (int index = 0; index < manualsUnlocked.length; index++) {
            int itemHeight = manualEntryHeight(index);
            if (itemY + itemHeight >= MANUAL_VIEW_Y
                    && itemY <= MANUAL_VIEW_Y + MANUAL_VIEW_H) {
                renderManualEntry(graphics, index, itemY, itemHeight, mouseX, mouseY);
            }
            itemY += itemHeight + 12;
        }
        graphics.disableScissor();
        renderManualScrollbar(graphics, mouseX, mouseY);
    }

    private void renderManualEntry(GuiGraphics graphics, int index, int y, int height,
                                   double mouseX, double mouseY) {
        int x = MANUAL_VIEW_X + 8;
        int width = MANUAL_VIEW_W - 28;
        boolean hovered = inside(mouseX, mouseY, x, Math.max(y, MANUAL_VIEW_Y), width,
                Math.min(height, MANUAL_VIEW_Y + MANUAL_VIEW_H - Math.max(y, MANUAL_VIEW_Y)));
        int border = hovered ? ARCHIVE_PALE : ARCHIVE_GOLD_DIM;
        drawArchivePanel(graphics, x, y, width, height, border);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.management_entry",
                        toRoman(index + 1)),
                x + width / 2, y + 10, 2.5F, ARCHIVE_GOLD, width - 24);
        graphics.fill(x + 16, y + 38, x + width - 16, y + 39, ARCHIVE_GOLD_DIM);

        if (manualsUnlocked[index]) {
            String content = index < manualTexts.length ? manualTexts[index]
                    : tr("screen.lobotocraft.common.unknown");
            drawWrappedScaledText(graphics, content, x + 16, y + 52,
                    2.0F, ARCHIVE_PALE, width - 32, Integer.MAX_VALUE);
        } else {
            renderUnlockPrompt(graphics, x + 16, y + 52, width - 32,
                    1, manualCost, hovered);
        }
    }


    private void renderManualScrollbar(GuiGraphics graphics, double mouseX, double mouseY) {
        int maximum = manualMaxScroll();
        if (maximum <= 0) return;
        int trackX = MANUAL_VIEW_X + MANUAL_VIEW_W - 9;
        int handleHeight = manualScrollbarHeight();
        int handleY = MANUAL_VIEW_Y + Mth.floor((MANUAL_VIEW_H - handleHeight)
                * (scrollOffset / maximum));
        graphics.fill(trackX, MANUAL_VIEW_Y, trackX + 5,
                MANUAL_VIEW_Y + MANUAL_VIEW_H, 0x663E3D2B);
        int color = isDraggingScrollbar || inside(mouseX, mouseY, trackX - 4, handleY,
                13, handleHeight) ? ARCHIVE_PALE : ARCHIVE_GOLD;
        graphics.fill(trackX, handleY, trackX + 5, handleY + handleHeight, color);
    }

    private void renderWorkPreferencePanel(GuiGraphics graphics, double mouseX, double mouseY) {
        drawArchivePanel(graphics, WORK_X, WORK_Y, WORK_W, WORK_H, ARCHIVE_PALE);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.work_preferences"),
                WORK_X, WORK_Y, WORK_W);
        if (isToolType) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.tool_no_work_preferences"),
                    WORK_X + WORK_W / 2, WORK_Y + 205, 3.0F, ARCHIVE_DIM, WORK_W - 50);
            return;
        }

        ResourceLocation[] icons = {INSTINCT_ICON, INSIGHT_ICON, ATTACHMENT_ICON, REPRESSION_ICON};
        String[] names = {
                tr("work.lobotocraft.instinct"), tr("work.lobotocraft.insight"),
                tr("work.lobotocraft.attachment"), tr("work.lobotocraft.repression")};
        for (int work = 0; work < 4; work++) {
            int col = work % 2;
            int row = work / 2;
            int x = WORK_X + 16 + col * 224;
            int y = WORK_Y + 62 + row * 181;
            int w = 214;
            int h = 166;
            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            int border = workPreferencesUnlocked[work]
                    ? (hovered ? ARCHIVE_PALE : ARCHIVE_GOLD_DIM) : 0xFF47473C;
            drawArchivePanel(graphics, x, y, w, h, border);
            graphics.blit(icons[work], x + 10, y + 46, 0, 0, 62, 62, 62, 62);
            drawScaledText(graphics, tr("screen.lobotocraft.encyclopedia.preference", names[work]),
                    x + 12, y + 11,
                    2.5F, workPreferencesUnlocked[work] ? ARCHIVE_PALE : ARCHIVE_DIM, w - 24);
            if (!workPreferencesUnlocked[work]) {
                graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xA9000000);
                renderUnlockPrompt(graphics, x + 18, y + 57, w - 36,
                        0, workPreferencesCost, hovered);
                continue;
            }
            for (int level = 0; level < 5; level++) {
                int lineY = y + 45 + level * 22;
                float rate = work < workPreferences.length
                        && workPreferences[work] != null
                        && level < workPreferences[work].length
                        ? workPreferences[work][level] : -1.0F;
                drawScaledText(graphics, toRoman(level + 1), x + 82, lineY,
                        2.0F, ARCHIVE_DIM, 32);
                int barX = x + 116;
                int barW = 60;
                graphics.fill(barX, lineY + 7, barX + barW, lineY + 11, 0xFF282A25);
                if (rate >= 0.0F) {
                    graphics.fill(barX, lineY + 7, barX + Mth.clamp((int) (barW * rate), 0, barW),
                            lineY + 11, EncyclopediaGUIConfig.Colors.getWorkRateColor(rate));
                }
                drawRightAlignedScaledText(graphics,
                        rate < 0.0F ? "/" : String.format("%.0f%%", rate * 100.0F),
                        x + w - 10, lineY, 2.0F,
                        rate < 0.0F ? ARCHIVE_DIM : ARCHIVE_PALE, 54);
            }
        }
    }

    private void renderSensitivePanel(GuiGraphics graphics, double mouseX, double mouseY) {
        drawArchivePanel(graphics, SENSITIVE_X, SENSITIVE_Y, SENSITIVE_W, SENSITIVE_H,
                isToolType ? ARCHIVE_GOLD_DIM : ARCHIVE_GOLD);
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.sensitive_information"),
                SENSITIVE_X, SENSITIVE_Y, SENSITIVE_W);
        if (isToolType) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.tool_no_sensitive_info"),
                    SENSITIVE_X + SENSITIVE_W / 2, SENSITIVE_Y + 105,
                    3.0F, ARCHIVE_DIM, SENSITIVE_W - 40);
            return;
        }
        boolean hovered = inside(mouseX, mouseY, SENSITIVE_X + 18, SENSITIVE_Y + 66,
                SENSITIVE_W - 36, SENSITIVE_H - 82);
        drawScaledText(graphics, tr("screen.lobotocraft.encyclopedia.qliphoth_counter_limit"),
                SENSITIVE_X + 26,
                SENSITIVE_Y + 82, 2.0F, ARCHIVE_GOLD, 230);
        if (sensitiveInfoUnlocked) {
            drawWrappedScaledText(graphics, compactSensitiveBehavior(), SENSITIVE_X + 26,
                    SENSITIVE_Y + 112, 1.8F, ARCHIVE_PALE, 225, 3);
            renderSensitiveResistances(graphics, SENSITIVE_X + 282, SENSITIVE_Y + 68);
        } else {
            renderUnlockPrompt(graphics, SENSITIVE_X + 282, SENSITIVE_Y + 70,
                    230, EncyclopediaGUIConfig.UnlockCosts.SENSITIVE_INFO_LEVEL,
                    sensitiveInfoCost, hovered);
        }
    }

    private void renderGiftPanel(GuiGraphics graphics) {
        drawArchivePanel(graphics, GIFT_X, GIFT_Y, GIFT_W, GIFT_H, ARCHIVE_PALE);
        IAbnormality current = abnormality;
        if (isToolType || current == null || current.getEGOGiftData() == null) {
            drawPanelTitle(graphics, tr("equipment.lobotocraft.ego_gift"), GIFT_X, GIFT_Y, GIFT_W);
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.gift_no_data"),
                    GIFT_X + GIFT_W / 2,
                    GIFT_Y + 84, 2.0F, ARCHIVE_DIM, GIFT_W - 40);
            return;
        }
        float probability = current.getGiftProbability() * 100.0F;
        String suffix = probability < 0.0F
                ? tr("screen.lobotocraft.encyclopedia.gift_rate_unknown")
                : tr("screen.lobotocraft.encyclopedia.gift_rate", String.format("%.1f", probability));
        drawPanelTitle(graphics, tr("screen.lobotocraft.encyclopedia.gift_title", suffix),
                GIFT_X, GIFT_Y, GIFT_W);
        if (canUnlockGift(current, observationLevel)) {
            renderArchiveGift(graphics, current);
        } else {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.encyclopedia.unlock_at_observation",
                            getRequiredLevelForGift(current)),
                    GIFT_X + GIFT_W / 2, GIFT_Y + 86, 2.5F, ARCHIVE_DIM, GIFT_W - 40);
        }
    }

    private void renderEgoPanels(GuiGraphics graphics) {
        renderEgoPanel(graphics, WEAPON_X, true);
        renderEgoPanel(graphics, ARMOR_X, false);
    }

    private void renderEgoPanel(GuiGraphics graphics, int x, boolean weapon) {
        drawArchivePanel(graphics, x, EGO_Y, EGO_W, EGO_H, ARCHIVE_PALE);
        drawPanelTitle(graphics, tr(weapon ? "equipment.lobotocraft.ego_weapon"
                : "equipment.lobotocraft.ego_armor"), x, EGO_Y, EGO_W);
        IAbnormality current = abnormality;
        boolean available = !isToolType && current != null
                && (weapon ? current.getEGOWeaponData() != null : current.getEGOArmorData() != null);
        if (!available) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.no_data"),
                    x + EGO_W / 2,
                    EGO_Y + 220, 3.0F, ARCHIVE_DIM, EGO_W - 30);
            return;
        }
        boolean unlocked = weapon ? canUnlockWeapon(current, observationLevel)
                : canUnlockArmor(current, observationLevel);
        if (!unlocked) {
            int required = weapon ? getRequiredLevelForWeapon(current)
                    : getRequiredLevelForArmor(current);
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.unlock_at_observation",
                            required),
                    x + EGO_W / 2, EGO_Y + 220, 2.5F, ARCHIVE_DIM, EGO_W - 30);
            return;
        }
        if (weapon) renderArchiveWeapon(graphics, x, current);
        else renderArchiveArmor(graphics, x, current);
    }

    private void renderArchiveGift(GuiGraphics graphics, IAbnormality current) {
        EGOEquipmentData.GiftData gift = current.getEGOGiftData();
        if (gift == null) return;
        int iconX = GIFT_X + 18;
        int iconY = GIFT_Y + 57;
        int iconSize = 72;
        ResourceLocation texture = current.renderCurio() ? gift.iconTexture() : QUESTION_MARK;
        graphics.fill(iconX - 2, iconY - 2, iconX + iconSize + 2,
                iconY + iconSize + 2, 0xFF3C3B2C);
        if (texture != null && ResourceUtil.exists(texture)) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(texture, iconX, iconY, 0, 0,
                    iconSize, iconSize, iconSize, iconSize);
        }
        drawScaledText(graphics, gift.name(), GIFT_X + 112, GIFT_Y + 59,
                2.0F, ARCHIVE_PALE, 400);
        drawScaledText(graphics, gift.part(), GIFT_X + 112, GIFT_Y + 87,
                2.0F, ARCHIVE_DIM, 400);
        drawWrappedScaledText(graphics, String.join(" / ", gift.effects()),
                GIFT_X + 112, GIFT_Y + 103, 1.5F, ARCHIVE_PALE, 400, 2);
    }

    private void renderArchiveWeapon(GuiGraphics graphics, int x, IAbnormality current) {
        EGOEquipmentData.WeaponData weapon = current.getEGOWeaponData();
        if (weapon == null) return;
        int iconX = x + 20;
        int iconY = EGO_Y + 58;
        int iconW = EGO_W - 40;
        int iconH = 164;
        graphics.fill(iconX, iconY, iconX + iconW, iconY + iconH, 0xFF210B0B);
        if (weapon.iconTexture() != null && ResourceUtil.exists(weapon.iconTexture())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(weapon.iconTexture(), iconX + 10, iconY + 7, 0, 0,
                    iconW - 20, iconH - 14, iconW - 20, iconH - 14);
        }
        drawCenteredScaledText(graphics, weapon.riskLevel().name(), x + EGO_W / 2,
                EGO_Y + 236, 2.0F, weapon.riskLevel().getColor(), EGO_W - 28);
        drawCenteredScaledText(graphics, weapon.name(), x + EGO_W / 2,
                EGO_Y + 263, 2.0F, ARCHIVE_PALE, EGO_W - 28);
        renderEgoMetric(graphics, x, EGO_Y + 302,
                tr("screen.lobotocraft.encyclopedia.attribute"), DamageTypeVisual.label(weapon.damageType()),
                EncyclopediaGUIConfig.Colors.getDamageTypeColor(
                        "ALL".equals(weapon.damageType()) ? "WHITE" : weapon.damageType()));
        renderEgoMetric(graphics, x, EGO_Y + 334,
                tr("screen.lobotocraft.encyclopedia.attack_power"), weapon.attackPower(), ARCHIVE_PALE);
        renderEgoMetric(graphics, x, EGO_Y + 366,
                tr("screen.lobotocraft.encyclopedia.attack_speed"), weapon.attackSpeed(), ARCHIVE_PALE);
        renderEgoMetric(graphics, x, EGO_Y + 398,
                tr("screen.lobotocraft.encyclopedia.attack_range"), weapon.attackRange(), ARCHIVE_PALE);
        renderDevelopmentBar(graphics, x, developmentWeaponCount,
                weapon.developmentMaxCount());
    }

    private void renderArchiveArmor(GuiGraphics graphics, int x, IAbnormality current) {
        EGOEquipmentData.ArmorData armor = current.getEGOArmorData();
        if (armor == null) return;
        int iconX = x + 20;
        int iconY = EGO_Y + 58;
        int iconW = EGO_W - 40;
        int iconH = 164;
        graphics.fill(iconX, iconY, iconX + iconW, iconY + iconH, 0xFF071D1D);
        if (armor.iconTexture() != null && ResourceUtil.exists(armor.iconTexture())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(armor.iconTexture(), iconX + 28, iconY + 5, 0, 0,
                    iconW - 56, iconH - 10, iconW - 56, iconH - 10);
        }
        drawCenteredScaledText(graphics, armor.riskLevel().name(), x + EGO_W / 2,
                EGO_Y + 236, 2.0F, armor.riskLevel().getColor(), EGO_W - 28);
        drawCenteredScaledText(graphics, armor.name(), x + EGO_W / 2,
                EGO_Y + 263, 2.0F, ARCHIVE_PALE, EGO_W - 28);
        renderResistanceMetric(graphics, x, EGO_Y + 302, "RED", armor.redResistance(),
                0xFFFF5A5A);
        renderResistanceMetric(graphics, x, EGO_Y + 334, "WHITE", armor.whiteResistance(),
                0xFFFFFFFF);
        renderResistanceMetric(graphics, x, EGO_Y + 366, "BLACK", armor.blackResistance(),
                0xFFC06DDE);
        renderResistanceMetric(graphics, x, EGO_Y + 398, "PALE", armor.paleResistance(),
                0xFF58DDEB);
        renderDevelopmentBar(graphics, x, developmentArmorCount,
                armor.developmentMaxCount());
    }

    private void renderEgoMetric(GuiGraphics graphics, int x, int y,
                                 String label, String value, int valueColor) {
        drawScaledText(graphics, label, x + 18, y, 2.0F, ARCHIVE_DIM, 104);
        drawRightAlignedScaledText(graphics, value, x + EGO_W - 18, y,
                2.0F, valueColor, 128);
        graphics.fill(x + 18, y + 24, x + EGO_W - 18, y + 25, 0xFF25271F);
    }

    private void renderResistanceMetric(GuiGraphics graphics, int x, int y,
                                        String type, float value, int color) {
        drawScaledText(graphics, type + " " + String.format("%.1f", value),
                x + 18, y, 2.0F, color, 130);
        drawRightAlignedScaledText(graphics, getResistanceLevel(value), x + EGO_W - 18,
                y, 1.5F, ARCHIVE_DIM, 112);
        graphics.fill(x + 18, y + 24, x + EGO_W - 18, y + 25, 0xFF25271F);
    }

    private void renderDevelopmentBar(GuiGraphics graphics, int x, int developed, int maximum) {
        int barY = EGO_Y + EGO_H - 42;
        boolean complete = developed >= maximum;
        graphics.fill(x + 12, barY, x + EGO_W - 12, barY + 30,
                complete ? 0xB53C5224 : 0xB5293A1B);
        drawScaledText(graphics, developed + "/" + maximum, x + 22, barY + 8,
                2.0F, complete ? ARCHIVE_GOLD : ARCHIVE_PALE, 100);
        drawRightAlignedScaledText(graphics, tr("screen.lobotocraft.encyclopedia.development"),
                x + EGO_W - 22, barY + 8,
                2.0F, ARCHIVE_GOLD, 82);
    }

    private String cleanObservationBonus(String text) {
        if (text == null || text.isBlank()) {
            return tr("screen.lobotocraft.encyclopedia.observation_unconfigured");
        }
        return text.replaceFirst("^\\s*(?:VIII|VII|VI|IV|V|III|II|I)\\s*[.、:-]?\\s*", "");
    }

    private String compactSensitiveInfo() {
        if (sensitiveInfoText == null || sensitiveInfoText.isBlank()) {
            return tr("screen.lobotocraft.common.unknown");
        }
        return sensitiveInfoText
                .replaceFirst("^\\s*计数器极限\\s*[：:]?\\s*", "")
                .replace(" | ", "\n")
                .replace("|", "\n")
                .trim();
    }

    private String compactSensitiveBehavior() {
        if (sensitiveInfoText == null || sensitiveInfoText.isBlank()) {
            return tr("screen.lobotocraft.common.unknown");
        }
        String text = SENSITIVE_RESISTANCE_CLAUSE_PATTERN.matcher(sensitiveInfoText)
                .replaceAll("");
        text = text.replaceAll("\\([^)]*抗性[^)]*\\)", "")
                .replaceAll("\\|\\s*\\|", "|")
                .replaceAll("^\\s*\\|+|\\|+\\s*$", "")
                .replace(" | ", "\n")
                .replace("|", "\n")
                .trim();
        return text.isBlank() ? tr("screen.lobotocraft.common.unknown") : text;
    }

    private List<SensitiveResistance> sensitiveResistances() {
        if (sensitiveInfoText == null || sensitiveInfoText.isBlank()) return List.of();
        List<SensitiveResistance> result = new ArrayList<>();
        Matcher matcher = SENSITIVE_RESISTANCE_PATTERN.matcher(sensitiveInfoText);
        while (matcher.find()) {
            String type = switch (matcher.group(1).toUpperCase(java.util.Locale.ROOT)) {
                case "红色", "RED" -> "RED";
                case "白色", "WHITE" -> "WHITE";
                case "黑色", "BLACK" -> "BLACK";
                default -> "PALE";
            };
            try {
                result.add(new SensitiveResistance(type, Float.parseFloat(matcher.group(2))));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy text and keep rendering the other entries.
            }
        }
        return result;
    }

    private void renderSensitiveResistances(GuiGraphics graphics, int x, int y) {
        List<SensitiveResistance> resistances = sensitiveResistances();
        if (resistances.isEmpty()) {
            drawWrappedScaledText(graphics, compactSensitiveInfo(), x, y + 8,
                    1.8F, ARCHIVE_PALE, 230, 4);
            return;
        }
        String[] order = {"RED", "WHITE", "BLACK", "PALE"};
        for (int index = 0; index < order.length; index++) {
            String type = order[index];
            SensitiveResistance resistance = resistances.stream()
                    .filter(entry -> entry.type().equals(type)).findFirst().orElse(null);
            if (resistance == null) continue;
            int rowY = y + index * 31;
            renderDamageTypeIcon(graphics, type, x, rowY, 20);
            int color = EncyclopediaGUIConfig.Colors.getDamageTypeColor(type);
            drawScaledText(graphics, type, x + 28, rowY + 2, 1.8F, color, 74);
            drawRightAlignedScaledText(graphics, String.format("%.1f", resistance.value()),
                    x + 226, rowY + 2, 1.8F, color, 64);
            drawRightAlignedScaledText(graphics, getResistanceLevel(resistance.value()),
                    x + 226, rowY + 17, 1.2F, ARCHIVE_DIM, 170);
        }
    }

    private void renderPeStorage(GuiGraphics graphics) {
        int x = 1330;
        int y = 914;
        int w = 340;
        int h = 126;
        drawArchivePanel(graphics, x, y, w, h, ARCHIVE_GOLD);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.pe_storage"),
                x + w / 2, y + 12,
                3.0F, ARCHIVE_PALE, w - 28);
        drawCenteredScaledText(graphics, String.valueOf(playerEnergy), x + w / 2,
                y + 55, 5.0F, playerEnergy > 0 ? ARCHIVE_PALE : ARCHIVE_DIM, w - 50);
    }

    private void renderExitButton(GuiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = inside(mouseX, mouseY, EXIT_X, EXIT_Y, EXIT_W, EXIT_H);
        graphics.fill(EXIT_X, EXIT_Y, EXIT_X + EXIT_W, EXIT_Y + EXIT_H,
                hovered ? 0xFFFF4B50 : ARCHIVE_RED);
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.common.exit"),
                EXIT_X + EXIT_W / 2, EXIT_Y + 15,
                5.0F, 0xFF090A08, EXIT_W - 24);
    }

    private void renderLockedArea(GuiGraphics graphics, int x, int y, int width, int height,
                                  int cost, int requiredLevel,
                                  double mouseX, double mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hovered ? 0xE0191A15 : 0xE0080908);
        renderUnlockPrompt(graphics, x + 20, y + height / 2 - 34, width - 40,
                requiredLevel, cost, hovered);
    }

    private void renderUnlockPrompt(GuiGraphics graphics, int x, int y, int width,
                                    int requiredLevel, int cost, boolean hovered) {
        if (requiredLevel > 0 && observationLevel < requiredLevel) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.requires_observation",
                            requiredLevel),
                    x + width / 2, y, 2.0F, ARCHIVE_RED, width);
            return;
        }
        int color = playerEnergy >= cost ? ARCHIVE_PALE : ARCHIVE_RED;
        drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.unlock_cost", cost),
                x + width / 2, y, 2.0F, color, width);
        if (hovered && playerEnergy >= cost) {
            drawCenteredScaledText(graphics, tr("screen.lobotocraft.encyclopedia.click_to_unlock"),
                    x + width / 2,
                    y + 28, 2.0F, ARCHIVE_GOLD, width);
        }
    }

    private void renderArchiveMessage(GuiGraphics graphics) {
        if (message == null || message.isEmpty()) return;
        int x = 540;
        int y = 1000;
        int w = 760;
        graphics.fill(x, y, x + w, y + 46, 0xEF171916);
        drawArchivePanel(graphics, x, y, w, 46, ARCHIVE_RED);
        drawCenteredScaledText(graphics, message, x + w / 2, y + 11,
                2.5F, 0xFFFFFFFF, w - 28);
    }

    private int manualEntryHeight(int index) {
        if (index < 0 || index >= manualsUnlocked.length || !manualsUnlocked[index]) return 96;
        String content = index < manualTexts.length ? manualTexts[index]
                : tr("screen.lobotocraft.common.unknown");
        int lines = Math.max(1, wrapScaledText(content, 2.0F, MANUAL_VIEW_W - 60).size());
        return Math.max(96, 66 + lines * scaledLineAdvance(2.0F));
    }

    private int manualContentHeight() {
        int height = 8;
        for (int index = 0; index < manualsUnlocked.length; index++) {
            height += manualEntryHeight(index);
            if (index + 1 < manualsUnlocked.length) height += 12;
        }
        return height + 8;
    }

    private int storyEntryHeight(int index) {
        if (index < 0 || index >= storyEntries.length) return 112;
        AbnormalityEncyclopediaData.StoryEntry entry = storyEntries[index];
        if (!entry.isUnlockedAt(observationLevel)) return 112;
        int lines = Math.max(1, wrapScaledText(entry.text(), 2.0F,
                STORY_VIEW_W - 94).size());
        return Math.max(112, 76 + lines * scaledLineAdvance(2.0F));
    }

    private int storyContentHeight() {
        int height = 14;
        for (int index = 0; index < storyEntries.length; index++) {
            height += storyEntryHeight(index);
            if (index + 1 < storyEntries.length) height += 18;
        }
        return height + 14;
    }

    private int activeArchiveContentHeight() {
        return showingStories ? storyContentHeight() : manualContentHeight();
    }

    private int manualMaxScroll() {
        return Math.max(0, activeArchiveContentHeight() - activeArchiveViewportHeight());
    }

    private int manualScrollbarHeight() {
        int viewportHeight = activeArchiveViewportHeight();
        int contentHeight = Math.max(viewportHeight, activeArchiveContentHeight());
        return Mth.clamp(viewportHeight * viewportHeight / contentHeight, 38, viewportHeight);
    }

    private int activeArchiveViewportX() {
        return showingStories ? STORY_VIEW_X : MANUAL_VIEW_X;
    }

    private int activeArchiveViewportY() {
        return showingStories ? STORY_VIEW_Y : MANUAL_VIEW_Y;
    }

    private int activeArchiveViewportWidth() {
        return showingStories ? STORY_VIEW_W : MANUAL_VIEW_W;
    }

    private int activeArchiveViewportHeight() {
        return showingStories ? STORY_VIEW_H : MANUAL_VIEW_H;
    }

    private int manualEntryAt(double mouseX, double mouseY) {
        if (showingStories) return -1;
        if (!inside(mouseX, mouseY, MANUAL_VIEW_X, MANUAL_VIEW_Y,
                MANUAL_VIEW_W, MANUAL_VIEW_H)) return -1;
        double contentY = mouseY - MANUAL_VIEW_Y - 8 + scrollOffset;
        int cursor = 0;
        for (int index = 0; index < manualsUnlocked.length; index++) {
            int height = manualEntryHeight(index);
            if (contentY >= cursor && contentY < cursor + height) return index;
            cursor += height + 12;
        }
        return -1;
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

        graphics.drawCenteredString(this.font, tr("screen.lobotocraft.encyclopedia.preview_3d"),
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
        renderDamageTypeIcon(graphics, damageType, x + 90, infoY - 5,
                EncyclopediaGUIConfig.Layout.CenterColumn.INFO_ICON_SIZE);
        graphics.drawString(this.font, DamageTypeVisual.label(damageType),
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
            int goodMin = getDisplayedGoodWorkResultMin();
            int goodMax = maxPEOutput;
            int normalMin = getDisplayedNormalWorkResultMin();
            int normalMax = Math.min(maxPEOutput, goodMin - 1);
            int badMin = 0;
            int badMax = normalMin - 1;

            graphics.drawString(this.font,
                    EncyclopediaGUIConfig.Text.PE_RANGE_GOOD + ": "
                            + (goodMin > goodMax ? "/" : goodMin + "-" + goodMax),
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

    private int getDisplayedGoodWorkResultMin() {
        if (this.abnormality != null) {
            return this.abnormality.getGoodWorkResultMin();
        }
        if ("O-03-89".equals(this.abnormalityCode)) {
            return 33;
        }
        if ("O-01-12".equals(this.abnormalityCode)) {
            return 11;
        }
        return (int) Math.ceil(maxPEOutput * 0.8D);
    }

    private int getDisplayedNormalWorkResultMin() {
        if (this.abnormality != null) {
            return this.abnormality.getNormalWorkResultMin();
        }
        if ("O-03-89".equals(this.abnormalityCode)) {
            return 17;
        }
        if ("O-01-12".equals(this.abnormalityCode)) {
            return 6;
        }
        return (int) Math.ceil(maxPEOutput * 0.4D);
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
        if (!abnormality.renderCurio()) {
            resourceLocation = QUESTION_MARK;
        }
        if (resourceLocation != null && ResourceUtil.exists(resourceLocation)) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + abnormality.getGiftRenderOffset()[0], y + abnormality.getGiftRenderOffset()[1], abnormality.getGiftRenderOffset()[2]);
            graphics.pose().scale(abnormality.getGiftRenderScale()[0], abnormality.getGiftRenderScale()[1], abnormality.getGiftRenderScale()[2]);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(resourceLocation, 0, 0, 0, 0, 70, 70, 70, 70);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
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
        graphics.drawCenteredString(this.font, tr("equipment.lobotocraft.ego_weapon"), x + width/2, y + 8,
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
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(resourceLocation, 0, 0, 0, 0,
                    iconSize, iconSize,   // 渲染尺寸
                    iconSize, iconSize);  // 纹理尺寸
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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
            String[] allTypes = {"RED", "PALE", "WHITE", "BLACK"};
            int iconDamageTypeSize = 12;
            int startX = attrX + 30;
            int startY = attrY - 3;
            int offset = 8; // 每个图标偏移量，产生叠放效果

            for (int i = 0; i < allTypes.length; i++) {
                renderDamageTypeIcon(graphics, allTypes[i],
                        startX + i * offset, startY + (i % 2 == 0 ? 0 : 2), iconDamageTypeSize);
            }

            // 绘制"全属性"文字
            graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.all_damage_types"),
                    attrX + 30 + offset * 3 + 5, attrY,
                    EncyclopediaGUIConfig.Colors.getDamageTypeColor("WHITE"));
        } else {
            // 单一属性：正常显示
            renderDamageTypeIcon(graphics, weaponData.damageType(), attrX + 30, attrY - 3, 12);
            graphics.drawString(this.font, DamageTypeVisual.label(weaponData.damageType()),
                    attrX + 45, attrY, EncyclopediaGUIConfig.Colors.getDamageTypeColor(weaponData.damageType()));
        }

        // 攻击力
        attrY += 15;
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.attack_power"),
                attrX, attrY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, weaponData.attackPower(),
                attrX + 60, attrY, EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 攻击速度
        attrY += 15;
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.attack_speed"),
                attrX, attrY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        graphics.drawString(this.font, weaponData.attackSpeed(),
                attrX + 60, attrY, EncyclopediaGUIConfig.Colors.TEXT_WHITE);

        // 攻击距离
        attrY += 15;
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.attack_range"),
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
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.development"),
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
        graphics.drawCenteredString(this.font, tr("equipment.lobotocraft.ego_armor"), x + width/2, y + 8,
                EncyclopediaGUIConfig.Colors.THEME_ORANGE);

        // 渲染护甲图标（居中，80x80）
        int iconSize = 80;
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + 25;

        ResourceLocation resourceLocation = armorData.iconTexture();
        if (resourceLocation != null && ResourceUtil.exists(resourceLocation)) {
            graphics.pose().pushPose();
            graphics.pose().translate(iconX + abnormality.getArmorRenderOffset()[0], iconY + abnormality.getArmorRenderOffset()[1], abnormality.getArmorRenderOffset()[2]);
            graphics.pose().scale(abnormality.getArmorRenderScale()[0], abnormality.getArmorRenderScale()[1], abnormality.getArmorRenderScale()[2]);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(resourceLocation, 0, 0, 0, 0,
                    iconSize, iconSize, iconSize, iconSize);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
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
        renderDamageTypeIcon(graphics, "RED", resX, resY - 3, 10);
        String redText = String.format("RED (%.1f)", armorData.redResistance());
        graphics.drawString(this.font, redText,
                resX + 13, resY, 0xFFFF6666);
        graphics.drawString(this.font, getResistanceLevel(armorData.redResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // WHITE抗性
        resY += lineHeight;
        renderDamageTypeIcon(graphics, "WHITE", resX, resY - 3, 10);
        String whiteText = String.format("WHITE (%.1f)", armorData.whiteResistance());
        graphics.drawString(this.font, whiteText,
                resX + 13, resY, 0xFFFFFFFF);
        graphics.drawString(this.font, getResistanceLevel(armorData.whiteResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // BLACK抗性
        resY += lineHeight;
        renderDamageTypeIcon(graphics, "BLACK", resX, resY - 3, 10);
        String blackText = String.format("BLACK (%.1f)", armorData.blackResistance());
        graphics.drawString(this.font, blackText,
                resX + 13, resY, 0xFFAA66FF);
        graphics.drawString(this.font, getResistanceLevel(armorData.blackResistance()),
                resX + 75, resY, EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        // PALE抗性
        resY += lineHeight;
        renderDamageTypeIcon(graphics, "PALE", resX, resY - 3, 10);
        String paleText = String.format("PALE (%.1f)", armorData.paleResistance());
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
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.development"),
                x + width - 30, y + height - 16, 0xFFFFAA00);
    }

    /**
     * 获取抗性等级文本（简化版）
     */
    private String getResistanceLevel(float value) {
        if (value >= 2.0f) return tr("screen.lobotocraft.encyclopedia.resistance.extremely_low");
        if (value >= 1.5f) return tr("screen.lobotocraft.encyclopedia.resistance.very_low");
        if (value > 1.0f) return tr("screen.lobotocraft.encyclopedia.resistance.low");
        if (value == 1.0f) return tr("screen.lobotocraft.encyclopedia.resistance.normal");
        if (value >= 0.8f) return tr("screen.lobotocraft.encyclopedia.resistance.high");
        if (value >= 0.5f) return tr("screen.lobotocraft.encyclopedia.resistance.very_high");
        return tr("screen.lobotocraft.encyclopedia.resistance.extremely_high");
    }

    private void renderDamageTypeIcon(GuiGraphics graphics, String type, int x, int y, int size) {
        DamageTypeVisual visual = DamageTypeVisual.from(type);
        ResourceLocation icon = visual.icon();
        if (icon != null) {
            LobotomyUiRenderer.blitScaled(graphics, icon, x, y, size, size,
                    visual.textureWidth(), visual.textureHeight());
        }
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
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.management_guidelines"),
                x, y,
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 动态渲染所有管理须知
        for (int i = 0; i < manualsUnlocked.length; i++) {
            String manualTitle = tr("screen.lobotocraft.encyclopedia.management_entry", toRoman(i + 1));
            String manualContent = i < manualTexts.length ? manualTexts[i]
                    : tr("screen.lobotocraft.encyclopedia.no_data");
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
        graphics.drawString(this.font, tr("screen.lobotocraft.encyclopedia.sensitive_information"),
                x, y,
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);
        y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

        // 逆卡巴拉计数极值
        renderTextOnlyEntry(graphics, x, y, tr("screen.lobotocraft.encyclopedia.qliphoth_counter_limit"),
                sensitiveInfoText,
                sensitiveInfoUnlocked,
                EncyclopediaGUIConfig.UnlockCosts.SENSITIVE_INFO_LEVEL,
                sensitiveInfoCost,
                mouseX, mouseY, 1);

        y += ENTRY_HEIGHT + 25;

        // 获取异想体实体
        IAbnormality abnormality = this.abnormality;
        if (abnormality == null) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.missing_abnormality"), 40);
            return;
        }

        // ========== E.G.O 饰品 ==========
        if (abnormality.getEGOGiftData() != null) {
            boolean giftUnlocked = canUnlockGift(abnormality, observationLevel);
            int giftRequiredLevel = getRequiredLevelForGift(abnormality);

            if (giftUnlocked) {
                // 已解锁：显示饰品和获取率
                float giftProb = abnormality.getGiftProbability() * 100;
                if (giftProb != -1f) {
                    graphics.drawString(this.font,
                            tr("screen.lobotocraft.encyclopedia.gift_title",
                                    tr("screen.lobotocraft.encyclopedia.gift_rate",
                                            String.format(java.util.Locale.ROOT, "%.1f", giftProb))),
                            x, y,
                            EncyclopediaGUIConfig.Colors.THEME_ORANGE);
                } else {
                    graphics.drawString(this.font,
                            tr("screen.lobotocraft.encyclopedia.gift_title",
                                    tr("screen.lobotocraft.encyclopedia.gift_rate_unknown")),
                            x, y,
                            EncyclopediaGUIConfig.Colors.THEME_ORANGE);
                }
                y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;
                renderEGOGift(graphics, x, y, abnormality);
                y += 85;
            } else {
                // 未解锁：显示需要的等级
                graphics.drawString(this.font,
                        tr("screen.lobotocraft.encyclopedia.gift_title",
                                tr("screen.lobotocraft.encyclopedia.unlock_at_observation", giftRequiredLevel)),
                        x, y, 0xFF888888);
                y += EncyclopediaGUIConfig.Layout.CenterColumn.TITLE_SPACING;

                // 绘制锁定框
                graphics.fill(x, y, x + 70, y + 70, 0xFF1A1A1A);
                graphics.drawCenteredString(this.font, tr("screen.lobotocraft.encyclopedia.locked"),
                        x + 35, y + 30, 0xFF666666);
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
                graphics.drawCenteredString(this.font, tr("equipment.lobotocraft.ego_armor"),
                        armorX + 75, y + 8, 0xFF888888);
                graphics.drawCenteredString(this.font,
                        tr("screen.lobotocraft.encyclopedia.unlock_at_observation", armorRequiredLevel),
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
                graphics.drawCenteredString(this.font, tr("equipment.lobotocraft.ego_weapon"),
                        weaponX + 75, y + 8, 0xFF888888);
                graphics.drawCenteredString(this.font,
                        tr("screen.lobotocraft.encyclopedia.unlock_at_observation", weaponRequiredLevel),
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
                EncyclopediaGUIConfig.Colors.TEXT_GRAY);

        if (unlocked) {
            drawWrappedText(graphics, content,
                    x + EncyclopediaGUIConfig.Layout.RightColumn.TEXT_PADDING_X,
                    y + 22, width - 20,
                    EncyclopediaGUIConfig.Colors.TEXT_GRAY);

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

    private static void drawArchivePanel(GuiGraphics graphics, int x, int y,
                                         int width, int height, int color) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_INNER);
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
        graphics.fill(x + 7, y + 7, x + 44, y + 8,
                LobotomyUiRenderer.withAlpha(color, 0.52F));
        graphics.fill(x + width - 44, y + height - 8, x + width - 7, y + height - 7,
                LobotomyUiRenderer.withAlpha(color, 0.52F));
    }

    private void drawPanelTitle(GuiGraphics graphics, String title,
                                int x, int y, int width) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + 49, PANEL_BLACK);
        for (int stripe = 0; stripe < Math.min(width - 24, 210); stripe += 28) {
            graphics.fill(x + 12 + stripe, y + 7, x + 29 + stripe, y + 13, ARCHIVE_GOLD_DIM);
        }
        drawRightAlignedScaledText(graphics, title, x + width - 14, y + 19,
                3.0F, ARCHIVE_GOLD, width - 32);
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y,
                                float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + x * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 3.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawCenteredScaledText(GuiGraphics graphics, String text, int centerX, int y,
                                        float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + centerX * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 3.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, -font.width(fitted) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawRightAlignedScaledText(GuiGraphics graphics, String text, int rightX, int y,
                                            float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + rightX * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 3.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, -font.width(fitted), 0, color, false);
        graphics.pose().popPose();
    }

    private void drawWrappedScaledText(GuiGraphics graphics, String text, int x, int y,
                                       float scale, int color, int maxWidth, int maxLines) {
        List<FormattedCharSequence> lines = wrapScaledText(text, scale, maxWidth);
        int count = Math.min(maxLines, lines.size());
        int advance = scaledLineAdvance(scale);
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + x * canvasScale);
        for (int index = 0; index < count; index++) {
            int screenY = Math.round(canvasY + (y + index * advance) * canvasScale);
            graphics.pose().pushPose();
            graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
            graphics.pose().translate(screenX - canvasX, screenY - canvasY, 3.0F);
            graphics.pose().scale(snappedScale, snappedScale, 1.0F);
            graphics.drawString(font, lines.get(index), 0, 0, color, false);
            graphics.pose().popPose();
        }
    }

    private List<FormattedCharSequence> wrapScaledText(String text, float scale, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        int wrapWidth = Math.max(1, screenMaxWidth / snappedScale);
        return font.split(Component.literal(text == null ? "" : text), wrapWidth);
    }

    private int scaledLineAdvance(float scale) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        return Math.max(14, Math.round((font.lineHeight + 2) * snappedScale
                / Math.max(0.0001F, canvasScale)));
    }

    private void enableLogicalScissor(GuiGraphics graphics, int x, int y,
                                      int width, int height) {
        int screenX = Math.round(canvasX + x * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenRight = Math.round(canvasX + (x + width) * canvasScale);
        int screenBottom = Math.round(canvasY + (y + height) * canvasScale);
        graphics.enableScissor(screenX, screenY, screenRight, screenBottom);
    }

    private double toLogicalX(double screenX) {
        return (screenX - canvasX) / Math.max(0.0001F, canvasScale);
    }

    private double toLogicalY(double screenY) {
        return (screenY - canvasY) / Math.max(0.0001F, canvasScale);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

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
        updateCanvasTransform();
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);
        if (inside(logicalX, logicalY, activeArchiveViewportX(), activeArchiveViewportY(),
                activeArchiveViewportWidth(), activeArchiveViewportHeight())) {
            targetScrollOffset = Mth.clamp(targetScrollOffset - scrollDelta * 54.0D,
                    0.0D, manualMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        updateCanvasTransform();
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);

        if (inside(logicalX, logicalY, EXIT_X, EXIT_Y, EXIT_W, EXIT_H)) {
            onClose();
            return true;
        }

        if (inside(logicalX, logicalY, HEADER_INFO_TAB_X, HEADER_TAB_Y,
                HEADER_TAB_W, HEADER_TAB_H)) {
            switchArchiveTab(false);
            return true;
        }
        if (inside(logicalX, logicalY, HEADER_STORY_TAB_X, HEADER_TAB_Y,
                HEADER_TAB_W, HEADER_TAB_H)) {
            switchArchiveTab(true);
            return true;
        }

        int maximum = manualMaxScroll();
        if (maximum > 0) {
            int viewportX = activeArchiveViewportX();
            int viewportY = activeArchiveViewportY();
            int viewportWidth = activeArchiveViewportWidth();
            int viewportHeight = activeArchiveViewportHeight();
            int trackX = showingStories
                    ? viewportX + viewportWidth - 14
                    : viewportX + viewportWidth - 13;
            int handleHeight = manualScrollbarHeight();
            int handleY = viewportY + Mth.floor((viewportHeight - handleHeight)
                    * (scrollOffset / maximum));
            if (inside(logicalX, logicalY, trackX, handleY, 16, handleHeight)) {
                isDraggingScrollbar = true;
                dragStartY = Mth.floor(logicalY);
                dragStartScroll = scrollOffset;
                return true;
            }
            if (inside(logicalX, logicalY, trackX, viewportY, 16, viewportHeight)) {
                double ratio = (logicalY - viewportY - handleHeight / 2.0D)
                        / Math.max(1.0D, viewportHeight - handleHeight);
                targetScrollOffset = Mth.clamp(ratio * maximum, 0.0D, maximum);
                return true;
            }
        }

        int manual = manualEntryAt(logicalX, logicalY);
        if (manual >= 0 && !manualsUnlocked[manual]) {
            return tryUnlock(manual + 3, manualCost, 1);
        }

        if (!basicInfoUnlocked && inside(logicalX, logicalY,
                BASIC_X, BASIC_Y, BASIC_W, BASIC_H)) {
            return tryUnlock(0, basicInfoCost, 0);
        }

        if (!isToolType) {
            for (int work = 0; work < 4; work++) {
                int x = WORK_X + 16 + (work % 2) * 224;
                int y = WORK_Y + 62 + (work / 2) * 181;
                if (!workPreferencesUnlocked[work]
                        && inside(logicalX, logicalY, x, y, 214, 166)) {
                    return tryUnlock(100 + work, workPreferencesCost, 0);
                }
            }
            if (!sensitiveInfoUnlocked && inside(logicalX, logicalY,
                    SENSITIVE_X, SENSITIVE_Y + 58, SENSITIVE_W, SENSITIVE_H - 58)) {
                return tryUnlock(2, sensitiveInfoCost,
                        EncyclopediaGUIConfig.UnlockCosts.SENSITIVE_INFO_LEVEL);
            }

            IAbnormality current = abnormality;
            if (current != null && egoDevelopmentBarAt(logicalX, logicalY, WEAPON_X)
                    && current.getEGOWeaponData() != null
                    && canUnlockWeapon(current, observationLevel)) {
                onWeaponDevelopmentClicked(current);
                return true;
            }
            if (current != null && egoDevelopmentBarAt(logicalX, logicalY, ARMOR_X)
                    && current.getEGOArmorData() != null
                    && canUnlockArmor(current, observationLevel)) {
                onArmorDevelopmentClicked(current);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && button == 0) {
            updateCanvasTransform();
            double logicalY = toLogicalY(mouseY);
            int maximum = manualMaxScroll();
            int handleHeight = manualScrollbarHeight();
            double trackRange = Math.max(1.0D,
                    activeArchiveViewportHeight() - handleHeight);
            double deltaScroll = (logicalY - dragStartY) / trackRange * maximum;
            targetScrollOffset = Mth.clamp(dragStartScroll + deltaScroll, 0.0D, maximum);
            scrollOffset = targetScrollOffset;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void switchArchiveTab(boolean stories) {
        if (showingStories == stories) return;
        showingStories = stories;
        scrollOffset = 0.0D;
        targetScrollOffset = 0.0D;
        isDraggingScrollbar = false;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ModSounds.UNLOCK_INFORMATION.get(), 0.35F, 1.15F);
        }
    }

    private boolean tryUnlock(int entryIndex, int cost, int requiredLevel) {
        if (requiredLevel > 0 && observationLevel < requiredLevel) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.observation_level",
                    requiredLevel, observationLevel), 40);
            return true;
        }
        if (playerEnergy < cost) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.pe_box",
                    cost, playerEnergy), 30);
            return true;
        }
        unlockEntry(entryIndex, cost);
        targetScrollOffset = Mth.clamp(targetScrollOffset, 0.0D, manualMaxScroll());
        return true;
    }

    private boolean egoDevelopmentBarAt(double mouseX, double mouseY, int panelX) {
        return inside(mouseX, mouseY, panelX + 12,
                EGO_Y + EGO_H - 42, EGO_W - 24, 30);
    }

    private void onWeaponDevelopmentClicked(IAbnormality abnormality) {
        if (abnormality.getWeaponDevelopmentCost() == -1) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.weapon_unavailable"), 30);
            return;
        }
        if (playerEnergy < abnormality.getWeaponDevelopmentCost()) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.weapon_cost",
                    abnormality.getWeaponDevelopmentCost()), 30);
            return;
        }

        // 打开制造界面
        if (minecraft != null) {
            minecraft.setScreen(new EgoResearchChoiceScreen(
                    "weapon",
                    abnormalityCode,
                    abnormalityId,
                    targetScrollOffset,
                    abnormality,
                    abnormality.getWeaponDevelopmentCost(),
                    this
            ));
        }
    }

    private void onArmorDevelopmentClicked(IAbnormality abnormality) {
        if (abnormality.getArmorDevelopmentCost() == -1) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.armor_unavailable"), 30);
            return;
        }
        if (playerEnergy < abnormality.getArmorDevelopmentCost()) {
            showErrorMessage(tr("screen.lobotocraft.encyclopedia.error.armor_cost",
                    abnormality.getArmorDevelopmentCost()), 30);
            return;
        }

        // 打开制造界面
        if (minecraft != null) {
            minecraft.setScreen(new EgoResearchChoiceScreen(
                    "armor",
                    abnormalityCode,
                    abnormalityId,
                    targetScrollOffset,
                    abnormality,
                    abnormality.getArmorDevelopmentCost(),
                    this
            ));
        }
    }

    private void unlockEntry(int entryIndex, int cost) {
        // 发送解锁请求到服务器
        MessageLoader.getLoader().sendToServer(
                new UnlockManualPacket(abnormalityCode, entryIndex, cost)
        );

        // Only the server's shared-knowledge reply may unlock an entry or spend PE.
    }

    public void refreshSharedKnowledge(com.wzz.lobotocraft.capability.PlayerAbnormalityData knowledge) {
        basicInfoUnlocked = knowledge.isManualUnlocked(abnormalityCode, 0);
        sensitiveInfoUnlocked = knowledge.isManualUnlocked(abnormalityCode, 2);
        for (int i = 0; i < workPreferencesUnlocked.length; i++) {
            workPreferencesUnlocked[i] = knowledge.isManualUnlocked(abnormalityCode, 100 + i);
        }
        for (int i = 0; i < manualsUnlocked.length; i++) {
            manualsUnlocked[i] = knowledge.isManualUnlocked(abnormalityCode, 3 + i);
        }
        observationLevel = knowledge.getObservationLevel(abnormalityCode);
        loadPlayerEnergy();
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
    public void onClose() {
        if (returnScreen != null && minecraft != null) {
            minecraft.setScreen(returnScreen);
            return;
        }
        super.onClose();
    }

    public Screen getReturnScreen() {
        return returnScreen;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
