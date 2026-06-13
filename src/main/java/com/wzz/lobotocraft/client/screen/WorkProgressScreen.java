package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ObtainGiftPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 工作进度界面
 * - 左侧：能量条（竖直排列，数量过多时自动双列）
 * - 右侧：文本框动画（淡入->停留->淡出）
 * - 右上角：工作计时器
 */
public class WorkProgressScreen extends Screen {

    // ==================== 贴图资源 ====================

    private static final ResourceLocation POSITIVE_ENERGY =
            ResourceUtil.createInstance("textures/gui/positive_energy.png");
    private static final ResourceLocation NEGATIVE_ENERGY =
            ResourceUtil.createInstance("textures/gui/negative_energy.png");

    private static final ResourceLocation DESC_1 = ResourceUtil.createInstance("textures/gui/desc_1.png");
    private static final ResourceLocation DESC_2 = ResourceUtil.createInstance("textures/gui/desc_2.png");
    private static final ResourceLocation DESC_3 = ResourceUtil.createInstance("textures/gui/desc_3.png");
    private static final ResourceLocation DESC_4 = ResourceUtil.createInstance("textures/gui/desc_4.png");

    private static final ResourceLocation RESULT_GOOD =
            ResourceUtil.createInstance("textures/gui/result_good.png");
    private static final ResourceLocation RESULT_NORMAL =
            ResourceUtil.createInstance("textures/gui/result_normal.png");
    private static final ResourceLocation RESULT_BAD =
            ResourceUtil.createInstance("textures/gui/result_bad.png");

    // ==================== 能量条配置 ====================

    private static final int BASE_ENERGY_ICON_SIZE = 32;
    private static final int BASE_ENERGY_SPACING = 40;
    private static final int MIN_ENERGY_ICON_SIZE = 12;
    private static final int MIN_ENERGY_SPACING = 15;
    private static final int LEFT_MARGIN = 20;
    private static final int MAX_BAR_HEIGHT_PERCENT = 80;

    private int energyIconSize;
    private int energySpacing;
    private boolean useDualColumn = false;  // 是否使用双列布局

    // ==================== 文本框动画配置 ====================

    private static final int DESC_FADE_IN_DURATION = 5;
    private static final int DESC_STAY_DURATION = 15;
    private static final int DESC_FADE_OUT_DURATION = 5;

    private static final int[][] DESC_SIZES = {
            {311, 57},  // DESC_1
            {214, 70},  // DESC_2
            {257, 91},  // DESC_3
            {196, 58}   // DESC_4
    };

    private enum SpawnDirection {
        TOP, BOTTOM, LEFT, RIGHT
    }

    // ==================== 工作数据 ====================

    private final int abnormalityId;
    private final WorkType workType;
    private final float successRate;
    private final int maxExtractions;
    private final int entityID;
    private int observationLevel;

    private int currentExtraction = 0;
    private int positiveCount = 0;
    private int negativeCount = 0;
    private boolean[] extractionResults;

    private WorkResult finalResult = null;
    private int finalPEOutput = 0;
    private int resultDisplayTimer = 0;
    private static final int RESULT_DISPLAY_TIME = 80;

    // ==================== 工作计时器（客户端本地计时） ====================

    /** 总 tick 数，每 tick +1，用于右上角显示 */
    private int workTimerTick = 0;

    // ==================== 文本框动画数据 ====================

    private final List<String> allDescriptions = new ArrayList<>();
    private final Random random = new Random();

    private int currentDescIndex = 0;
    private int currentDescFrameIndex = 0;
    private int descAnimationTimer = 0;
    private DescAnimationPhase descPhase = DescAnimationPhase.FADE_IN;
    private SpawnDirection currentDirection;
    private int targetX, targetY;

    private enum DescAnimationPhase {
        FADE_IN, STAY, FADE_OUT
    }

    // ==================== 工作日志配置 ====================

    private static final int LOG_FADE_IN_DURATION = 10;
    private static final int LOG_STAY_DURATION = 60;
    private static final int LOG_FADE_OUT_DURATION = 10;

    private List<String> workLogs = new ArrayList<>();
    private int currentLogIndex = 0;
    private int logAnimationTimer = 0;
    private LogAnimationPhase logPhase = LogAnimationPhase.IDLE;

    private enum LogAnimationPhase {
        IDLE, TYPING, STAY, FADE_OUT
    }

    private float speedMultiplier = 1.0f;

    public WorkProgressScreen(int abnormalityId, WorkType workType, float successRate, int maxExtractions, int entityID, int observationLevel) {
        super(Component.literal("工作进行中"));
        this.minecraft = Minecraft.getInstance();
        this.abnormalityId = abnormalityId;
        this.workType = workType;
        this.successRate = successRate;
        this.maxExtractions = maxExtractions;
        this.extractionResults = new boolean[maxExtractions];
        this.entityID = entityID;
        this.observationLevel = observationLevel;
        calculateDynamicSizes();
        initializeDescriptions();
        selectRandomDescFrame();
        currentDirection = SpawnDirection.TOP;
        initializeWorkLogs();
    }

    private void initializeWorkLogs() {
        IAbnormality abnormality = getAbnormality();
        if (abnormality == null) {
            ModLogger.error("初始化工作日志失败：异想体为null");
            return;
        }
        this.workLogs = abnormality.getWorkLogs();
        startWorkLogs();
    }

    private IAbnormality getAbnormality() {
        Entity entity = null;
        if (minecraft != null) {
            if (minecraft.level != null) {
                entity = minecraft.level.getEntity(entityID);
            }
        }
        if (entity instanceof IAbnormality abnormality) {
            return abnormality;
        }
        return null;
    }

    /**
     * 动态计算能量图标大小，并决定是否使用双列布局。
     * 策略：
     * 1. 先尝试单列，从 BASE 开始缩放到 MIN。
     * 2. 若单列 MIN 仍然放不下（超过 MAX_BAR_HEIGHT_PERCENT），切换为双列再重新计算。
     */
    private void calculateDynamicSizes() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) {
            energyIconSize = BASE_ENERGY_ICON_SIZE;
            energySpacing = BASE_ENERGY_SPACING;
            useDualColumn = false;
            return;
        }

        int screenHeight = this.height > 0 ? this.height : 240;
        int maxBarHeight = screenHeight * MAX_BAR_HEIGHT_PERCENT / 100;

        // —— 先尝试单列 ——
        int idealTotalHeight = maxExtractions * BASE_ENERGY_SPACING;
        if (idealTotalHeight <= maxBarHeight) {
            // 单列、基础尺寸即可
            energyIconSize = BASE_ENERGY_ICON_SIZE;
            energySpacing = BASE_ENERGY_SPACING;
            useDualColumn = false;
            return;
        }

        // 单列需要压缩
        float compressionRatio = (float) maxBarHeight / idealTotalHeight;
        int compressedSize    = Math.max(MIN_ENERGY_ICON_SIZE, (int)(BASE_ENERGY_ICON_SIZE * compressionRatio));
        int compressedSpacing = Math.max(MIN_ENERGY_SPACING,   (int)(BASE_ENERGY_SPACING   * compressionRatio));

        int compressedTotalHeight = maxExtractions * compressedSpacing;
        if (compressedTotalHeight <= maxBarHeight) {
            // 压缩后单列够用
            energyIconSize = compressedSize;
            energySpacing  = compressedSpacing;
            useDualColumn  = false;
            return;
        }

        // —— 单列 MIN 也放不下，切换双列 ——
        // 双列每列只需放 ceil(maxExtractions / 2) 个格子
        useDualColumn = true;
        int halfCount = (maxExtractions + 1) / 2;
        int dualIdealHeight = halfCount * BASE_ENERGY_SPACING;

        if (dualIdealHeight <= maxBarHeight) {
            energyIconSize = BASE_ENERGY_ICON_SIZE;
            energySpacing  = BASE_ENERGY_SPACING;
        } else {
            float dualRatio = (float) maxBarHeight / dualIdealHeight;
            energyIconSize = Math.max(MIN_ENERGY_ICON_SIZE, (int)(BASE_ENERGY_ICON_SIZE * dualRatio));
            energySpacing  = Math.max(MIN_ENERGY_SPACING,   (int)(BASE_ENERGY_SPACING   * dualRatio));
        }
    }

    private void initializeDescriptions() {
        allDescriptions.add("正在确认工作进程");
        allDescriptions.add("清扫地面污物");
        allDescriptions.add("开始全面分析");
        allDescriptions.add("检查知识需求");
        allDescriptions.add("确认感知与记忆");
        allDescriptions.add("分析压力原因");
        allDescriptions.add("调节照明强度");
        allDescriptions.add("检查内部机动");
        allDescriptions.add("测试思考过程");
        allDescriptions.add("检查音响系统");
        allDescriptions.add("测试问题解决能力");
        allDescriptions.add("确认系统正常运作");
        allDescriptions.add("分析外界感知");
        allDescriptions.add("检查成长速度");
        allDescriptions.add("分析行为模式");
        allDescriptions.add("检查空气指标");
        allDescriptions.add("测试音量稳定");

        currentDescIndex = random.nextInt(allDescriptions.size());
    }

    private void selectRandomDescFrame() {
        currentDescFrameIndex = random.nextInt(4);
        currentDirection = SpawnDirection.values()[random.nextInt(4)];
        generateRandomPosition();
    }

    private void generateRandomPosition() {
        if (this.width == 0 || this.height == 0) {
            targetX = 100;
            targetY = 100;
            return;
        }

        int frameWidth  = DESC_SIZES[currentDescFrameIndex][0];
        int frameHeight = DESC_SIZES[currentDescFrameIndex][1];
        int margin = 50;

        switch (currentDirection) {
            case TOP -> {
                targetX = margin + random.nextInt(Math.max(1, this.width - frameWidth - margin * 2));
                targetY = margin;
            }
            case BOTTOM -> {
                targetX = margin + random.nextInt(Math.max(1, this.width - frameWidth - margin * 2));
                targetY = this.height - frameHeight - margin;
            }
            case LEFT -> {
                targetX = margin;
                targetY = margin + random.nextInt(Math.max(1, this.height - frameHeight - margin * 2));
            }
            case RIGHT -> {
                targetX = this.width - frameWidth - margin;
                targetY = margin + random.nextInt(Math.max(1, this.height - frameHeight - margin * 2));
            }
        }
    }

    // ==================== 数据更新方法 ====================

    @Override
    public void init() {
        super.init();
        calculateDynamicSizes();
        generateRandomPosition();
    }

    public void onExtractionReceived(boolean success, int extractionCount,
                                     int successCount, int failureCount) {
        this.currentExtraction = extractionCount;
        this.positiveCount = successCount;
        this.negativeCount = failureCount;

        int index = extractionCount - 1;
        if (index >= 0 && index < maxExtractions) {
            extractionResults[index] = success;
        }

        if (minecraft != null && minecraft.player != null) {
            if (success) {
                minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
            } else {
                minecraft.player.playSound(SoundEvents.PLAYER_HURT, 0.5F, 0.8F);
            }
        }
    }

    public void onWorkComplete(WorkResult result, int peOutput) {
        this.finalResult = result;
        this.finalPEOutput = peOutput;
        if (minecraft != null && minecraft.player != null) {
            switch (result) {
                case GOOD -> minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
                case NORMAL -> minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                case BAD -> minecraft.player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
            }
            if (observationLevel >= 2) {
                IAbnormality abnormality = getAbnormality();
                try {
                    if (abnormality != null && random.nextFloat() <= abnormality.getGiftProbability()) {
                        MessageLoader.getLoader().sendToServer(new ObtainGiftPacket(abnormality.getEntityId(), abnormality.getEGOGiftData().itemId()));
                        minecraft.player.sendSystemMessage(Component.literal("§a你获得了 §e" + abnormality.getAbnormalityName()
                                + " §a异想体的 §e" + abnormality.getEGOGiftData().part() + " §a饰品：§6" + abnormality.getEGOGiftData().name()));
                        minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP);
                    }
                } catch (Throwable e) {
                    minecraft.player.sendSystemMessage(Component.literal("§c饰品获得失败：异想体消失或离你过远"));
                }
            }
        }
    }

    public void startWorkLogs() {
        if (workLogs != null && !workLogs.isEmpty()) {
            currentLogIndex = 0;
            logPhase = LogAnimationPhase.TYPING;
            logAnimationTimer = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 结果显示倒计时
        if (finalResult != null) {
            resultDisplayTimer++;
            if (resultDisplayTimer >= RESULT_DISPLAY_TIME) {
                this.onClose();
            }
            return;
        }

        // 工作计时器（工作未完成时每 tick 累加）
        workTimerTick += (int) speedMultiplier;

        // 更新文本框动画
        descAnimationTimer++;
        switch (descPhase) {
            case FADE_IN -> {
                if (descAnimationTimer >= DESC_FADE_IN_DURATION) {
                    descPhase = DescAnimationPhase.STAY;
                    descAnimationTimer = 0;
                }
            }
            case STAY -> {
                if (descAnimationTimer >= DESC_STAY_DURATION) {
                    descPhase = DescAnimationPhase.FADE_OUT;
                    descAnimationTimer = 0;
                }
            }
            case FADE_OUT -> {
                if (descAnimationTimer >= DESC_FADE_OUT_DURATION) {
                    descPhase = DescAnimationPhase.FADE_IN;
                    descAnimationTimer = 0;
                    currentDescIndex = random.nextInt(allDescriptions.size());
                    selectRandomDescFrame();
                }
            }
        }

        updateWorkLogAnimation();
    }

    public void setSpeedMultiplier(float multiplier) {
        this.speedMultiplier = multiplier;
    }

    private void updateWorkLogAnimation() {
        if (logPhase == LogAnimationPhase.IDLE || workLogs == null) return;

        logAnimationTimer++;

        switch (logPhase) {
            case TYPING -> {
                if (logAnimationTimer >= LOG_FADE_IN_DURATION) {
                    logPhase = LogAnimationPhase.STAY;
                    logAnimationTimer = 0;
                }
            }
            case STAY -> {
                if (logAnimationTimer >= LOG_STAY_DURATION) {
                    logPhase = LogAnimationPhase.FADE_OUT;
                    logAnimationTimer = 0;
                }
            }
            case FADE_OUT -> {
                if (logAnimationTimer >= LOG_FADE_OUT_DURATION) {
                    if (workLogs.size() > 1) {
                        int previousIndex = currentLogIndex;
                        do {
                            currentLogIndex = random.nextInt(workLogs.size());
                        } while (currentLogIndex == previousIndex);
                    } else {
                        currentLogIndex = 0;
                    }
                    logPhase = LogAnimationPhase.TYPING;
                    logAnimationTimer = 0;
                }
            }
        }
    }

    // ==================== 界面自适应缩放(只缩不放,不改变正常分辨率下的显示样式) ====================
    private static final int MIN_UI_WIDTH = 360;
    private static final int MIN_UI_HEIGHT = 260;
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
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        renderEnergyBar(graphics);
        renderWorkTimer(graphics);   // ← 右上角计时器

        if (finalResult != null) {
            renderFinalResult(graphics);
        } else {
            renderDescriptionBox(graphics, partialTick);
            renderWorkLog(graphics, partialTick);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== 渲染：右上角工作计时器 ====================

    /**
     * 右上角显示工作已用时间，格式 "工作时间 MM:SS"。
     */
    private void renderWorkTimer(GuiGraphics graphics) {
        long totalSeconds = workTimerTick / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String timeText = String.format("工作时间  %02d:%02d", minutes, seconds);

        int textWidth = this.font.width(timeText);
        int padding   = 4;
        int bgX = this.width - textWidth - padding * 2 - 8;
        int bgY = 6;
        int bgW = textWidth + padding * 2;
        int bgH = this.font.lineHeight + padding;

        // 半透明深色背景
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.fill(bgX, bgY, bgX + bgW, bgY + bgH, 0xAA000000);
        // 细边框
        graphics.fill(bgX,          bgY,          bgX + bgW,     bgY + 1,      0xFF444444);
        graphics.fill(bgX,          bgY + bgH - 1, bgX + bgW,    bgY + bgH,    0xFF444444);
        graphics.fill(bgX,          bgY,          bgX + 1,        bgY + bgH,   0xFF444444);
        graphics.fill(bgX + bgW - 1, bgY,         bgX + bgW,     bgY + bgH,    0xFF444444);
        RenderSystem.disableBlend();

        // 文字：黄色计时数字
        graphics.drawString(this.font, "工作时间  ", bgX + padding, bgY + padding / 2, 0xAAAAAA, false);
        String countPart = String.format("%02d:%02d", minutes, seconds);
        int countX = bgX + padding + this.font.width("工作时间  ");
        graphics.drawString(this.font, countPart, countX, bgY + padding / 2, 0xFFFF55, false);
    }

    // ==================== 渲染：工作日志 ====================

    private void renderWorkLog(GuiGraphics graphics, float partialTick) {
        if (workLogs == null || logPhase == LogAnimationPhase.IDLE || workLogs.isEmpty() || currentLogIndex >= workLogs.size()) {
            return;
        }

        String currentLog = workLogs.get(currentLogIndex);

        int centerX = this.width / 2;
        int logY = this.height * 2 / 3 + 60;

        int textWidth = this.font.width(currentLog);
        int padding   = 16;
        int bgWidth   = textWidth + padding * 2;
        int bgHeight  = this.font.lineHeight + padding;
        int bgX       = centerX - bgWidth / 2;

        float progress = (logAnimationTimer + partialTick);
        float alpha = 1.0f;
        int visibleChars = currentLog.length();

        switch (logPhase) {
            case TYPING -> {
                float typingProgress = progress / LOG_FADE_IN_DURATION;
                visibleChars = (int)(currentLog.length() * Math.min(typingProgress, 1.0f));
                alpha = 1.0f;
            }
            case STAY -> {
                visibleChars = currentLog.length();
                alpha = 1.0f;
            }
            case FADE_OUT -> {
                visibleChars = currentLog.length();
                float fadeProgress = progress / LOG_FADE_OUT_DURATION;
                alpha = 1.0f - Math.min(fadeProgress, 1.0f);
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bgAlpha     = (int)(alpha * 180);
        int bgColor     = (bgAlpha << 24) | 0x2A2A2A;
        graphics.fill(bgX, logY, bgX + bgWidth, logY + bgHeight, bgColor);

        int borderAlpha = (int)(alpha * 220);
        int borderColor = (borderAlpha << 24) | 0x555555;
        graphics.fill(bgX,           logY,            bgX + bgWidth, logY + 1,           borderColor);
        graphics.fill(bgX,           logY + bgHeight - 1, bgX + bgWidth, logY + bgHeight, borderColor);
        graphics.fill(bgX,           logY,            bgX + 1,       logY + bgHeight,    borderColor);
        graphics.fill(bgX + bgWidth - 1, logY,        bgX + bgWidth, logY + bgHeight,    borderColor);

        String displayText = currentLog.substring(0, visibleChars);
        int textAlpha  = (int)(alpha * 255);
        int textColor  = (textAlpha << 24) | 0xFFFFFF;

        int textX = centerX - this.font.width(displayText) / 2;
        int textY = logY + padding / 2;

        graphics.drawString(this.font, displayText, textX, textY, textColor);

        if (logPhase == LogAnimationPhase.TYPING && visibleChars < currentLog.length()) {
            if ((logAnimationTimer / 5) % 2 == 0) {
                int cursorX = textX + this.font.width(displayText);
                graphics.drawString(this.font, "_", cursorX, textY, textColor);
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ==================== 渲染：能量条（左侧，支持双列） ====================

    /**
     * 渲染能量条。
     *
     * - 单列：与原逻辑相同，从下往上排列。
     * - 双列：左列放前半段，右列放后半段，均从下往上排列。
     *   左列索引 0 .. halfCount-1（显示在左），右列索引 halfCount .. maxExtractions-1（显示在右）。
     */
    private void renderEnergyBar(GuiGraphics graphics) {
        if (useDualColumn) {
            renderEnergyBarDual(graphics);
        } else {
            renderEnergyBarSingle(graphics);
        }
    }

    /** 单列能量条（原逻辑） */
    private void renderEnergyBarSingle(GuiGraphics graphics) {
        int x = LEFT_MARGIN;
        int startY = this.height / 2 - (maxExtractions * energySpacing) / 2;

        int barWidth  = energyIconSize + 10;
        int barHeight = maxExtractions * energySpacing + 10;
        drawBarBackground(graphics, x - 5, startY - 5, x + barWidth, startY + barHeight);

        for (int i = 0; i < maxExtractions; i++) {
            int currentY = startY + (maxExtractions - 1 - i) * energySpacing;
            if (i < currentExtraction) {
                renderEnergyIcon(graphics, x, currentY, extractionResults[i], true);
            } else {
                renderEnergyIcon(graphics, x, currentY, false, false);
            }
        }

        drawStats(graphics, x, startY + barHeight + 10);
    }

    /** 双列能量条：数量过多时分两列显示 */
    private void renderEnergyBarDual(GuiGraphics graphics) {
        int halfCount  = (maxExtractions + 1) / 2;
        int colGap     = 6;  // 两列之间的间距

        // 整体背景宽度 = 左列 + 间距 + 右列
        int colWidth   = energyIconSize + 4;
        int totalWidth = colWidth * 2 + colGap + 10;
        int startY     = this.height / 2 - (halfCount * energySpacing) / 2;
        int barHeight  = halfCount * energySpacing + 10;

        int bgX = LEFT_MARGIN - 5;
        drawBarBackground(graphics, bgX, startY - 5, bgX + totalWidth, startY + barHeight);

        int leftColX  = LEFT_MARGIN;
        int rightColX = LEFT_MARGIN + colWidth + colGap;

        for (int i = 0; i < maxExtractions; i++) {
            boolean isLeft = (i < halfCount);
            // 左列：索引 0..halfCount-1；右列：索引 halfCount..maxExtractions-1
            int posInCol = isLeft ? i : (i - halfCount);
            int rowFromBottom = (isLeft ? halfCount : (maxExtractions - halfCount)) - 1 - posInCol;
            int x = isLeft ? leftColX : rightColX;
            int y = startY + rowFromBottom * energySpacing;

            if (i < currentExtraction) {
                renderEnergyIcon(graphics, x, y, extractionResults[i], true);
            } else {
                renderEnergyIcon(graphics, x, y, false, false);
            }
        }

        drawStats(graphics, LEFT_MARGIN, startY + barHeight + 10);
    }

    private void drawBarBackground(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1,     y1,     x2,     y2,     0xCC000000);
        graphics.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, 0xFF1A1A1A);
    }

    private void drawStats(GuiGraphics graphics, int x, int statsY) {
        graphics.drawString(this.font, "正: " + positiveCount, x, statsY,      0x00FFFF);
        graphics.drawString(this.font, "负: " + negativeCount, x, statsY + 12, 0xFF0000);
        graphics.drawString(this.font,
                String.format("%d/%d", currentExtraction, maxExtractions),
                x, statsY + 28, 0xFFFFFF);
    }

    private void renderEnergyIcon(GuiGraphics graphics, int x, int y,
                                  boolean isPositive, boolean isFilled) {
        if (!isFilled) {
            graphics.fill(x, y, x + energyIconSize, y + energyIconSize, 0xFF333333);
            graphics.fill(x + 2, y + 2, x + energyIconSize - 2,
                    y + energyIconSize - 2, 0xFF1A1A1A);
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            ResourceLocation icon = isPositive ? POSITIVE_ENERGY : NEGATIVE_ENERGY;
            graphics.blit(icon, x, y, 0, 0,
                    energyIconSize, energyIconSize,
                    energyIconSize, energyIconSize);
        }
    }

    // ==================== 渲染：描述文本框 ====================

    private void renderDescriptionBox(GuiGraphics graphics, float partialTick) {
        ResourceLocation[] descFrames = {DESC_1, DESC_2, DESC_3, DESC_4};
        ResourceLocation currentFrame = descFrames[currentDescFrameIndex];

        int frameWidth  = DESC_SIZES[currentDescFrameIndex][0];
        int frameHeight = DESC_SIZES[currentDescFrameIndex][1];

        float alpha = 1.0f;
        float progress = (descAnimationTimer + partialTick);

        switch (descPhase) {
            case FADE_IN  -> alpha = Math.min(progress / DESC_FADE_IN_DURATION, 1.0f);
            case STAY     -> alpha = 1.0f;
            case FADE_OUT -> alpha = 1.0f - Math.min(progress / DESC_FADE_OUT_DURATION, 1.0f);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.blit(currentFrame, targetX, targetY, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);

        String text    = allDescriptions.get(currentDescIndex);
        int textWidth  = this.font.width(text);
        int textX      = targetX + (frameWidth  - textWidth)          / 2;
        int textY      = targetY + (frameHeight - this.font.lineHeight) / 2;

        int textAlpha = (int)(alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        graphics.drawString(this.font, text, textX, textY, textColor);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ==================== 渲染：最终结果 ====================

    private void renderFinalResult(GuiGraphics graphics) {
        int centerX = this.width  / 2;
        int centerY = this.height / 2;

        int panelWidth  = 300;
        int panelHeight = 180;
        int panelX = centerX - panelWidth  / 2;
        int panelY = centerY - panelHeight / 2;

        graphics.fill(panelX - 2, panelY - 2,
                panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFFFFAA00);
        graphics.fill(panelX, panelY,
                panelX + panelWidth, panelY + panelHeight, 0xFF000000);

        ResourceLocation resultIcon = switch (finalResult) {
            case GOOD   -> RESULT_GOOD;
            case NORMAL -> RESULT_NORMAL;
            case BAD    -> RESULT_BAD;
        };

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(resultIcon, centerX - 32, panelY + 20,
                0, 0, 64, 64, 64, 64);

        String resultText = switch (finalResult) {
            case GOOD   -> "工作结果：优";
            case NORMAL -> "工作结果：良";
            case BAD    -> "工作结果：差";
        };

        graphics.drawCenteredString(this.font, resultText,
                centerX, panelY + 95, finalResult.getColor());

        String peText = "获得 PE-BOX: " + finalPEOutput;
        graphics.drawCenteredString(this.font, peText,
                centerX, panelY + 115, 0xFFFF00);

        String statsText = String.format("成功: %d  失败: %d", positiveCount, negativeCount);
        graphics.drawCenteredString(this.font, statsText,
                centerX, panelY + 135, 0xAAAAAA);

        // 显示本次工作总时长
        long totalSec = workTimerTick / 20;
        long min = totalSec / 60, sec = totalSec % 60;
        String durationText = String.format("用时 %02d:%02d", min, sec);
        graphics.drawCenteredString(this.font, durationText,
                centerX, panelY + 148, 0x88AAFF);

        int remaining = (RESULT_DISPLAY_TIME - resultDisplayTimer) / 20;
        graphics.drawCenteredString(this.font, "自动关闭中... " + remaining + "秒",
                centerX, panelY + 162, 0x666666);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return finalResult != null;
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
