package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.IEmployeeStats;
import com.wzz.lobotocraft.client.compat.YsmClientCompat;
import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.client.ui.FacilityPauseButton;
import com.wzz.lobotocraft.compat.ysm.YsmModelCatalog;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import com.wzz.lobotocraft.employee.PlayerProfileService;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import com.wzz.lobotocraft.item.ego.base.IEgoLevelItem;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.EquipPersonalEgoPacket;
import com.wzz.lobotocraft.network.packet.AssignAiEgoPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.RequestLlmConfigPacket;
import com.wzz.lobotocraft.network.packet.OpenEmployeeWarehousePacket;
import com.wzz.lobotocraft.network.packet.RequestPlayerProfilePacket;
import com.wzz.lobotocraft.network.packet.SetEmployeeAppearancePacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public final class FacilityManagementScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 720;
    private static final int PANEL_MAX_HEIGHT = 390;
    private static final int HEADER_HEIGHT = 66;
    private static final int EGO_ROW_HEIGHT = LobotomyUiTheme.ROW_EGO;
    private static final int EMPLOYEE_ROW_HEIGHT = LobotomyUiTheme.ROW_EMPLOYEE;
    private static final String[] TAB_KEYS = {
            "screen.lobotocraft.facility.tab.employees",
            "screen.lobotocraft.facility.tab.personal_ego",
            "screen.lobotocraft.facility.tab.ai_ego"};

    // 四维属性展示（theme/stat_*.png 存在时画 8×8 图标，否则单字 + STAT_* 着色）
    private static final String[] STAT_LABEL_KEYS = {
            "stat.lobotocraft.fortitude.short",
            "stat.lobotocraft.prudence.short",
            "stat.lobotocraft.temperance.short",
            "stat.lobotocraft.justice.short"};
    private static final String[] STAT_KEYS = {"Fortitude", "Prudence", "Temperance", "Justice"};
    private static final int[] STAT_COLORS = {LobotomyUiTheme.STAT_FORTITUDE, LobotomyUiTheme.STAT_PRUDENCE,
            LobotomyUiTheme.STAT_TEMPERANCE, LobotomyUiTheme.STAT_JUSTICE};
    private static final ResourceLocation[] STAT_ICONS = {
            ResourceUtil.createInstance("textures/gui/theme/stat_fortitude.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_prudence.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_temperance.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_justice.png")};

    // ===== 员工状态色 =====
    // 原版 AgentSlot 的 FilterFill/FilterText 色。Theme 未提供的两色在本文件内定义（不改 Theme）。
    private static final int STATE_PANIC = 0xFFB05CD6;          // 精神崩溃（恐慌）紫
    private static final int STATE_UNCONTROLLABLE = 0xFFE05A2B; // 失控（暴走）橙红
    /** 等级徽章固定宽度：定宽才能保证各行员工名左缘对齐。 */
    private static final int GRADE_BADGE_W = 14;
    // 持有者槽位（原版 Inventory/InventorySlot 的 ownerSlot 列表）
    private static final int OWNER_SLOT_W = 7;
    private static final int OWNER_SLOT_H = 9;
    private static final int OWNER_SLOT_GAP = 2;
    private static final int OWNER_SLOT_MAX = 8;

    /**
     * 员工状态 —— 对齐原版 CommandWindow/AgentSlot 的 {@code AgentState}。
     * <p>{@code filterAlpha} 对应原版 {@code FilterFill} 的整行滤镜强度，0 表示不画滤镜
     * （原版 IDLE 会 {@code FilterControl.SetActive(false)}）；{@code tintRow} 对应原版对
     * {@code coloredTargets} 调 {@code SetColor(...)}：仅 DEAD/PANIC/UNCONTROLLABLE
     * 三种异常态把整行元素一起染成状态色。
     */
    private enum AgentState {
        DEAD("status.lobotocraft.dead", LobotomyUiTheme.ALERT_RED, 0.30F, true),
        PANIC("status.lobotocraft.panic", STATE_PANIC, 0.24F, true),
        CONTROLLED("status.lobotocraft.controlled", STATE_UNCONTROLLABLE, 0.24F, true),
        UNCONTROLLABLE("status.lobotocraft.uncontrollable", STATE_UNCONTROLLABLE, 0.24F, true),
        WORKING("status.lobotocraft.working", LobotomyUiTheme.GOLD, 0.16F, false),
        SUPPRESSING("status.lobotocraft.suppressing", LobotomyUiTheme.ALERT_RED, 0.16F, false),
        MOVING("status.lobotocraft.moving", LobotomyUiTheme.WARN_YELLOW, 0.16F, false),
        IDLE("status.lobotocraft.idle", LobotomyUiTheme.TEXT_MUTED, 0.0F, false),
        OFFLINE("status.lobotocraft.offline", LobotomyUiTheme.TEXT_MUTED, 0.0F, false),
        ONLINE("status.lobotocraft.online", LobotomyUiTheme.TEXT_MUTED, 0.0F, false);

        final String translationKey;
        final int color;
        final float filterAlpha;
        final boolean tintRow;

        AgentState(String translationKey, int color, float filterAlpha, boolean tintRow) {
            this.translationKey = translationKey;
            this.color = color;
            this.filterAlpha = filterAlpha;
            this.tintRow = tintRow;
        }
    }

    /**
     * 单个员工行的预计算视图。snapshot 在本屏生命周期内不可变（刷新会重建整个 Screen），
     * 因此所有派生字符串在首帧一次性算好并缓存，渲染路径上不再做拼接/装箱。
     */
    private static final class EmployeeRowView {
        final UUID id;
        final boolean ai;
        final boolean vitals;
        final String name;
        final String subtitle;
        final String egoText;
        final String grade;
        final String stateText;
        final String[] statValues;
        final String hpText;
        final String spText;
        final String hpShort;
        final String spShort;
        final float hp;
        final float maxHp;
        final float sp;
        final float maxSp;
        final int deptColor;
        final boolean deptKnown;
        final AgentState state;

        EmployeeRowView(UUID id, boolean ai, boolean vitals, String name, String subtitle, String egoText,
                        String grade, String stateText, String[] statValues, float hp, float maxHp,
                        float sp, float maxSp, int deptColor, boolean deptKnown, AgentState state) {
            this.id = id;
            this.ai = ai;
            this.vitals = vitals;
            this.name = name;
            this.subtitle = subtitle;
            this.egoText = egoText;
            this.grade = grade;
            this.stateText = stateText;
            this.statValues = statValues;
            this.hp = hp;
            this.maxHp = maxHp;
            this.sp = sp;
            this.maxSp = maxSp;
            this.hpText = "HP " + clampToZero(hp) + "/" + clampToZero(maxHp);
            this.spText = "SP " + clampToZero(sp) + "/" + clampToZero(maxSp);
            this.hpShort = String.valueOf(clampToZero(hp));
            this.spShort = String.valueOf(clampToZero(sp));
            this.deptColor = deptColor;
            this.deptKnown = deptKnown;
            this.state = state;
        }

        /** 原版 AgentSlot：{@code if (num < 0) num = 0;} —— 当前值为负一律显示 0。 */
        private static int clampToZero(float value) {
            return Math.max(0, Math.round(value));
        }
    }

    private CompoundTag snapshot;
    private EmployeeRowView[] employeeRows;
    private int activeTab;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private UUID selectedAiEmployee;
    private String selectedAiName = "";
    private LobotomyButton departmentButton;
    private LobotomyButton appearanceButton;
    private LobotomyButton pauseButton;
    private static UUID rememberedAiEmployee;

    public FacilityManagementScreen(CompoundTag snapshot) {
        super(Component.translatable("screen.lobotocraft.facility.title"));
        this.snapshot = snapshot.copy();
        restoreEmployeeSelection();
    }

    public void updateSnapshot(CompoundTag updated) {
        UUID keep = selectedAiEmployee;
        snapshot = updated.copy();
        employeeRows = null;
        selectedAiEmployee = null;
        selectedAiName = "";
        if (keep != null) rememberedAiEmployee = keep;
        restoreEmployeeSelection();
        updateLayout();
        int visibleHeight = panelHeight - HEADER_HEIGHT - 44 - (activeTab == 2 ? 18 : 0);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - Math.max(1, visibleHeight)));
    }

    @Override
    public void init() {
        updateLayout();
        if (snapshot.getBoolean("CanPause")) {
            pauseButton = new FacilityPauseButton(panelX + 112, panelY + 14, 82, 20);
            addRenderableWidget(pauseButton);
        }
        int profileRight = panelX + panelWidth - 16;
        int specialWidth = panelWidth >= 520 ? 86 : 72;
        int identityWidth = panelWidth >= 520 ? 76 : 54;
        LobotomyButton specialEgo = LobotomyButton.create(
                        Component.translatable("screen.lobotocraft.facility.special_ego"), button ->
                                MessageLoader.getLoader().sendToServer(new RequestPlayerProfilePacket(
                                        PlayerProfileService.View.SPECIAL_EGO)))
                .bounds(profileRight - specialWidth, panelY + 43, specialWidth, 18).build();
        specialEgo.setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.facility.special_ego.tooltip")));
        addRenderableWidget(specialEgo);
        LobotomyButton identity = LobotomyButton.create(
                        Component.translatable(panelWidth >= 520
                                ? "screen.lobotocraft.facility.my_department"
                                : "screen.lobotocraft.facility.department"), button ->
                                MessageLoader.getLoader().sendToServer(new RequestPlayerProfilePacket(
                                        PlayerProfileService.View.DEPARTMENT)))
                .bounds(profileRight - specialWidth - identityWidth - 6,
                        panelY + 43, identityWidth, 18).build();
        identity.setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.facility.my_department.tooltip")));
        addRenderableWidget(identity);
        if (snapshot.getBoolean("CanManageAi")) {
            int appearanceWidth = panelWidth >= 520 ? 58 : 48;
            appearanceButton = LobotomyButton.create(
                            Component.translatable("screen.lobotocraft.common.appearance"), button -> openAppearanceScreen())
                    .bounds(profileRight - specialWidth - identityWidth - appearanceWidth - 18,
                            panelY + 43, appearanceWidth, 18).build();
            appearanceButton.setTooltip(Tooltip.create(
                    Component.translatable("screen.lobotocraft.facility.appearance.tooltip")));
            appearanceButton.active = canEditSelectedAppearance();
            addRenderableWidget(appearanceButton);
        }
        if (snapshot.getBoolean("CanRecruit")) {
            LobotomyButton warehouse = LobotomyButton.create(Component.translatable(
                    "screen.lobotocraft.facility.warehouse"), button ->
                            MessageLoader.getLoader().sendToServer(new OpenEmployeeWarehousePacket()))
                    .bounds(panelX + panelWidth - 278, panelY + 14, 52, 20).build();
            warehouse.setTooltip(Tooltip.create(Component.translatable(
                    "screen.lobotocraft.facility.warehouse.tooltip")));
            addRenderableWidget(warehouse);
            LobotomyButton recruit = LobotomyButton.create(Component.translatable(
                    "screen.lobotocraft.facility.recruit"), button ->
                            minecraft.setScreen(new RecruitmentScreen(this, snapshot)))
                    .bounds(panelX + panelWidth - 218, panelY + 14, 48, 20).build();
            recruit.setTooltip(Tooltip.create(Component.translatable(
                    "screen.lobotocraft.facility.recruit.tooltip")));
            addRenderableWidget(recruit);
        }
        if (snapshot.getBoolean("CanManageAi")) {
            departmentButton = LobotomyButton.create(Component.translatable(
                    "screen.lobotocraft.facility.department"), button -> openDepartmentScreen())
                    .bounds(panelX + panelWidth - 162, panelY + 14, 48, 20).build();
            departmentButton.setTooltip(Tooltip.create(Component.translatable(
                    "screen.lobotocraft.facility.department.tooltip")));
            departmentButton.active = hasAiEmployees();
            addRenderableWidget(departmentButton);
        }
        if (snapshot.getBoolean("CanConfigureLlm")) {
            LobotomyButton llm = LobotomyButton.create(Component.literal("LLM"), button ->
                            MessageLoader.getLoader().sendToServer(new RequestLlmConfigPacket()))
                    .bounds(panelX + panelWidth - 106, panelY + 14, 40, 20).build();
            llm.setTooltip(Tooltip.create(Component.translatable(
                    "screen.lobotocraft.facility.llm.tooltip")));
            addRenderableWidget(llm);
        }
        LobotomyButton refresh = LobotomyButton.create(Component.literal("R"), button ->
                        MessageLoader.getLoader().sendToServer(new RequestFacilityManagementPacket()))
                .bounds(panelX + panelWidth - 58, panelY + 14, 20, 20).build();
        refresh.setTooltip(Tooltip.create(Component.translatable("screen.lobotocraft.common.refresh")));
        addRenderableWidget(refresh);
        LobotomyButton close = LobotomyButton.create(Component.literal("X"), button -> onClose())
                .bounds(panelX + panelWidth - 30, panelY + 14, 20, 20).build();
        close.setTooltip(Tooltip.create(Component.translatable("screen.lobotocraft.common.close")));
        addRenderableWidget(close);
    }

    private void updateLayout() {
        panelWidth = Math.max(1, Math.min(PANEL_MAX_WIDTH, width - 8));
        panelHeight = Math.max(1, Math.min(PANEL_MAX_HEIGHT, height - 8));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        updateLayout();
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "FACILITY CONTROL", tr("screen.lobotocraft.facility.subtitle"));
        boolean llmEnabled = snapshot.getBoolean("LlmEnabled");
        LobotomyUiRenderer.drawTag(graphics, font, panelX + LobotomyUiTheme.PAD, panelY + 44,
                tr(llmEnabled ? "screen.lobotocraft.facility.llm.ready"
                        : "screen.lobotocraft.facility.llm.offline"),
                llmEnabled ? LobotomyUiTheme.ACCENT : LobotomyUiTheme.TEXT_MUTED);
        renderTabs(graphics, mouseX, mouseY);
        if (departmentButton != null) departmentButton.active = hasAiEmployees();
        if (appearanceButton != null) appearanceButton.active = canEditSelectedAppearance();

        int listTop = panelY + HEADER_HEIGHT + 30;
        int listBottom = panelY + panelHeight - 14;
        graphics.enableScissor(panelX + 12, listTop, panelX + panelWidth - 12, listBottom);
        if (activeTab == 0) renderEmployees(graphics, listTop, mouseX, mouseY);
        else if (activeTab == 1) renderPersonalEgo(graphics, listTop, mouseX, mouseY);
        else renderAiEgo(graphics, listTop, mouseX, mouseY);
        graphics.disableScissor();
        LobotomyUiRenderer.drawScrollbar(graphics, panelX + panelWidth - 16, listTop, listBottom,
                contentHeight(), scroll);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderContextTooltip(graphics, mouseX, mouseY, listTop);
    }

    private int contentHeight() {
        return switch (activeTab) {
            case 0 -> snapshot.getList("Employees", Tag.TAG_COMPOUND).size() * EMPLOYEE_ROW_HEIGHT;
            case 1 -> snapshot.getList("PersonalEgo", Tag.TAG_COMPOUND).size() * EGO_ROW_HEIGHT;
            default -> 18 + snapshot.getList("AiEgo", Tag.TAG_COMPOUND).size() * EGO_ROW_HEIGHT;
        };
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = panelY + HEADER_HEIGHT;
        int tabWidth = (panelWidth - 32) / TAB_KEYS.length;
        int hoveredIndex = tabIndexAt(mouseX, mouseY);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int x = panelX + 16 + i * tabWidth;
            LobotomyUiRenderer.drawTab(graphics, font, x, y, tabWidth - 2, 24,
                    tr(TAB_KEYS[i]), i == activeTab, i == hoveredIndex);
        }
    }

    /** 标签命中判定（render 悬停与 mouseClicked 共用，区域与改造前完全一致）。 */
    private int tabIndexAt(double mouseX, double mouseY) {
        int tabY = panelY + HEADER_HEIGHT;
        if (mouseY < tabY || mouseY >= tabY + 24) return -1;
        int tabWidth = (panelWidth - 32) / TAB_KEYS.length;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int x = panelX + 16 + i * tabWidth;
            if (mouseX >= x && mouseX < x + tabWidth - 2) return i;
        }
        return -1;
    }

    private void renderEmployees(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        EmployeeRowView[] rows = employeeRows();
        if (rows.length == 0) {
            drawEmpty(graphics, top, tr("screen.lobotocraft.facility.employees.empty"));
            return;
        }
        boolean wide = panelWidth >= 560;
        int rowX = panelX + 14;
        int rowW = panelWidth - 28;
        int nameWidth = wide ? 138 : 92;
        int detailX = panelX + (wide ? 178 : 128);
        int vitalsWidth = wide ? 154 : 88;
        int vitalsX = panelX + panelWidth - vitalsWidth - 24;
        // 宽面板给状态文字一条专属竖列（原版 FilterText 的显著位置），窄面板复用
        // 详情列第二行（该行的 E.G.O 文本在窄面板本就不画），避免挤压四维属性。
        int stateWidth = wide ? 76 : 0;
        int stateX = vitalsX - stateWidth - 8;
        int detailWidth = Math.max(48, (wide ? stateX : vitalsX) - detailX - 8);
        for (int i = 0; i < rows.length; i++) {
            EmployeeRowView row = rows[i];
            int y = top + i * EMPLOYEE_ROW_HEIGHT - scroll;
            if (!rowVisible(y, top, EMPLOYEE_ROW_HEIGHT)) continue;
            boolean hovered = inside(mouseX, mouseY, rowX, y + 2, rowW, EMPLOYEE_ROW_HEIGHT - 4);
            boolean selected = row.ai && row.id != null && row.id.equals(selectedAiEmployee);
            int rowTop = y + 2;
            int rowBottom = y + EMPLOYEE_ROW_HEIGHT - 2;
            LobotomyUiRenderer.drawRow(graphics, rowX, rowTop, rowW, EMPLOYEE_ROW_HEIGHT - 4,
                    i, hovered, selected);

            // 原版 FilterControl/FilterFill：整行半透明状态色滤镜，压在行底之上、文字之下。
            AgentState state = row.state;
            if (state.filterAlpha > 0.0F) {
                graphics.fill(rowX, rowTop, rowX + rowW, rowBottom,
                        LobotomyUiRenderer.withAlpha(state.color, state.filterAlpha));
            }
            if (selected) {
                // 覆盖 drawRow 的通用强调竖条，改为部门色
                graphics.fill(rowX, rowTop, rowX + 2, rowBottom, row.deptColor);
            }
            // 原版 SetColor(coloredTargets)：异常态（死亡/崩溃/失控）整行元素统一染成状态色。
            boolean tinted = state.tintRow;
            int nameColor = tinted ? state.color : LobotomyUiTheme.TEXT_TITLE;
            int subtitleColor = tinted ? state.color
                    : row.deptKnown ? row.deptColor
                    : row.ai ? LobotomyUiTheme.ACCENT_DIM : LobotomyUiTheme.TEXT_MUTED;
            int mutedColor = tinted ? LobotomyUiRenderer.withAlpha(state.color, 0.70F)
                    : LobotomyUiTheme.TEXT_MUTED;

            graphics.fill(panelX + 18, y + 6, panelX + 21, y + EMPLOYEE_ROW_HEIGHT - 6,
                    tinted ? state.color
                            : row.deptKnown ? row.deptColor
                            : row.ai ? LobotomyUiTheme.ACCENT_DIM : LobotomyUiTheme.LINE_MID);
            // 原版 Grade sprite（DeployUI.GetAgentGradeSprite）：此处以等级徽章方块还原。
            int textX = panelX + 29;
            drawGradeBadge(graphics, textX, y + 4, row.grade,
                    tinted ? state.color : row.deptKnown ? row.deptColor : LobotomyUiTheme.ACCENT);
            textX += GRADE_BADGE_W + 4;
            int labelWidth = Math.max(32, nameWidth - GRADE_BADGE_W - 4);
            graphics.drawString(font, fit(row.name, labelWidth), textX, y + 5, nameColor, false);
            graphics.drawString(font, fit(row.subtitle, labelWidth), textX, y + 18, subtitleColor, false);

            drawStats(graphics, row, detailX, y + 5, detailWidth, tinted ? state.color : 0);
            if (wide) {
                drawMarqueeText(graphics, row.egoText, detailX, y + 18,
                        detailWidth, mutedColor);
            }

            // 原版 HealthText/HealthFill：数值与填充条同时存在。宽面板给完整 "当前/上限"，
            // 窄面板只留当前值，避免与状态文字互相挤压。
            if (row.vitals) {
                LobotomyUiRenderer.drawDualBars(graphics, vitalsX, y + 20, vitalsWidth,
                        row.hp, row.maxHp, row.sp, row.maxSp);
                drawVitalsText(graphics, row, vitalsX, y + 4, vitalsWidth, wide);
            } else {
                graphics.fill(vitalsX, y + 22, vitalsX + vitalsWidth, y + 25, LobotomyUiTheme.BAR_TRACK);
            }

            // 原版 FilterText：状态文字压在滤镜上，不再是右侧小胶囊。
            drawStateText(graphics, row, rowTop, wide ? stateX : detailX,
                    wide ? stateWidth : detailWidth, wide);
        }
    }

    /**
     * 状态文字（原版 FilterText）。宽面板在专属状态列内居中；窄面板落在详情列第二行左对齐。
     * 空闲态原版 {@code FilterControl.SetActive(false)}，此处 stateText 为空直接不画。
     */
    private void drawStateText(GuiGraphics graphics, EmployeeRowView row, int rowTop,
                               int x, int width, boolean wide) {
        String text = row.stateText;
        if (text.isEmpty()) return;
        String fitted = fit(text, Math.max(16, width));
        if (wide) {
            int textX = x + (width - font.width(fitted)) / 2;
            int textY = rowTop + (EMPLOYEE_ROW_HEIGHT - 4 - 8) / 2;
            if (row.state.filterAlpha > 0.0F) {
                // 状态列内一段更浓的状态色底，让文字从整行滤镜里跳出来
                graphics.fill(x, rowTop + 3, x + width, rowTop + EMPLOYEE_ROW_HEIGHT - 7,
                        LobotomyUiRenderer.withAlpha(row.state.color, 0.22F));
            }
            graphics.drawString(font, fitted, textX, textY, row.state.color, false);
        } else {
            graphics.drawString(font, fitted, x, rowTop + 16, row.state.color, false);
        }
    }

    /**
     * HP/SP 数值（原版 HealthText / MentalText，与填充条并存）。
     * 宽面板画 "HP 当前/上限"+"SP 当前/上限"；宽度不足时自动降级为只画当前值。
     */
    private void drawVitalsText(GuiGraphics graphics, EmployeeRowView row,
                                int x, int y, int width, boolean wide) {
        int spColor = spTextColor(row);
        if (wide && font.width(row.hpText) + font.width(row.spText) + 10 <= width) {
            graphics.drawString(font, row.hpText, x + 2, y, LobotomyUiTheme.BAR_HP, false);
            graphics.drawString(font, row.spText, x + 2 + font.width(row.hpText) + 8, y,
                    spColor, false);
            return;
        }
        graphics.drawString(font, row.hpShort, x + 2, y, LobotomyUiTheme.BAR_HP, false);
        graphics.drawString(font, row.spShort, x + 2 + font.width(row.hpShort) + 6, y,
                spColor, false);
    }

    /** 等级徽章（原版 Grade sprite 的 2D 还原）：定宽描边小方块 + 罗马数字。 */
    private void drawGradeBadge(GuiGraphics graphics, int x, int y, String grade, int color) {
        graphics.fill(x, y, x + GRADE_BADGE_W, y + 26, LobotomyUiRenderer.withAlpha(color, 0.16F));
        graphics.fill(x, y, x + 1, y + 26, color);
        graphics.drawString(font, grade, x + 1 + (GRADE_BADGE_W - 1 - font.width(grade)) / 2, y + 9,
                color, false);
    }

    /** SP 数值色与 drawDualBars 内部规则一致：< 25% 转警戒红。 */
    private static int spTextColor(EmployeeRowView row) {
        return row.sp / Math.max(1.0F, row.maxSp) < 0.25F
                ? LobotomyUiTheme.BAR_SP_LOW : LobotomyUiTheme.BAR_SP;
    }

    /**
     * 员工行视图缓存。snapshot 是构造期 copy 的不可变快照（刷新走 RequestFacilityManagementPacket
     * → 新建 Screen），所以只在首帧构建一次，之后每帧零分配。
     */
    private EmployeeRowView[] employeeRows() {
        if (employeeRows != null) return employeeRows;
        ListTag entries = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        EmployeeRowView[] rows = new EmployeeRowView[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            rows[i] = buildEmployeeRow(entries.getCompound(i));
        }
        employeeRows = rows;
        return rows;
    }

    private EmployeeRowView buildEmployeeRow(CompoundTag entry) {
        boolean ai = "AI_EMPLOYEE".equals(entry.getString("ActorType"));
        String deptId = entry.getString("DepartmentId");
        if (deptId.isBlank()) deptId = departmentId(entry.getString("Department"));
        boolean deptKnown = deptId != null && !"unassigned".equals(deptId);
        AgentState state = agentState(entry, ai);
        String[] statValues = new String[STAT_KEYS.length];
        for (int s = 0; s < STAT_KEYS.length; s++) {
            statValues[s] = String.valueOf(entry.getInt(STAT_KEYS[s]));
        }
        String stateText = state == AgentState.IDLE ? "" : tr(state.translationKey);
        String department = departmentName(entry);
        return new EmployeeRowView(
                entry.hasUUID("Id") ? entry.getUUID("Id") : null, ai,
                !ai || entry.getBoolean("Deployed"),
                entry.getString("Name"),
                tr(ai ? "screen.lobotocraft.facility.employee.ai"
                        : "screen.lobotocraft.facility.employee.player", department),
                tr("screen.lobotocraft.facility.employee.ego",
                        egoName(entry, "Weapon"), egoName(entry, "Armor")),
                roman(Math.max(1, entry.getInt("EmployeeLevel"))),
                stateText, statValues,
                entry.getFloat("Health"), entry.getFloat("MaxHealth"),
                entry.getFloat("Mental"), entry.getFloat("MaxMental"),
                LobotomyUiTheme.departmentColor(deptId), deptKnown, state);
    }

    /**
     * 本模组状态 → 原版 {@code AgentState} 映射。
     * <p>死亡与失控在本模组的快照里没有独立字段，按脑叶的判定规则从生命/精神值推出：
     * HP ≤ 0 → DEAD（原版 agent.IsDead()）；恐慌状态由服务端按 SP 恢复阈值判定。
     * 崩溃且正在镇压任务上 → UNCONTROLLABLE（原版：失控员工会攻击同僚）。
     */
    private static AgentState agentState(CompoundTag entry, boolean ai) {
        if (!ai) {
            if (entry.getFloat("Health") <= 0.0F) return AgentState.DEAD;
            if ("CONTROLLED".equals(entry.getString("Status"))) return AgentState.CONTROLLED;
            if (entry.getBoolean("Panicking")) return AgentState.PANIC;
            return AgentState.ONLINE;
        }
        if (!entry.getBoolean("Deployed")) return AgentState.OFFLINE;
        if (entry.getFloat("Health") <= 0.0F) return AgentState.DEAD;
        String status = entry.getString("Status");
        if ("CONTROLLED".equals(status)) return AgentState.CONTROLLED;
        if (entry.getBoolean("Panicking")) {
            return "SEARCH_AND_SUPPRESS".equals(status)
                    ? AgentState.UNCONTROLLABLE : AgentState.PANIC;
        }
        if (!entry.getString("NavigationPurpose").isBlank()) return AgentState.MOVING;
        return switch (status) {
            case "WORK" -> AgentState.WORKING;
            case "SEARCH_AND_SUPPRESS" -> AgentState.SUPPRESSING;
            case "MOVE", "FOLLOW", "RETURN_TO_DEPARTMENT" -> AgentState.MOVING;
            default -> AgentState.IDLE;
        };
    }

    /**
     * 四维属性：图标（theme/stat_*.png 就位时）或着色单字 + 数值，超出宽度自动截断。
     * {@code tintColor} 非 0 时（异常态整行染色）覆盖单字与数值色。
     */
    private void drawStats(GuiGraphics graphics, EmployeeRowView row, int x, int y,
                           int maxWidth, int tintColor) {
        int cursor = x;
        int limit = x + maxWidth;
        int valueColor = tintColor != 0 ? tintColor : LobotomyUiTheme.TEXT_BODY;
        for (int s = 0; s < STAT_KEYS.length; s++) {
            String value = row.statValues[s];
            boolean icon = tintColor == 0 && LobotomyUiRenderer.textureExists(STAT_ICONS[s]);
            String statLabel = tr(STAT_LABEL_KEYS[s]);
            int labelWidth = icon ? 8 : font.width(statLabel);
            if (cursor + labelWidth + 2 + font.width(value) > limit) break;
            if (icon) {
                // 素材约定 16×16（见 DESIGN_PLAN 1.8 节），缩放到 8×8 绘制
                LobotomyUiRenderer.blitScaled(graphics, STAT_ICONS[s], cursor, y, 8, 8, 16, 16);
            } else {
                graphics.drawString(font, statLabel, cursor, y,
                        tintColor != 0 ? tintColor : STAT_COLORS[s], false);
            }
            cursor += labelWidth + 2;
            graphics.drawString(font, value, cursor, y, valueColor, false);
            cursor += font.width(value) + 6;
        }
    }

    private void renderPersonalEgo(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        ListTag entries = snapshot.getList("PersonalEgo", Tag.TAG_COMPOUND);
        if (entries.isEmpty()) {
            drawEmpty(graphics, top, tr("screen.lobotocraft.facility.personal_ego.empty"));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int y = top + i * EGO_ROW_HEIGHT - scroll;
            if (!rowVisible(y, top, EGO_ROW_HEIGHT)) continue;
            LobotomyUiRenderer.drawRow(graphics, panelX + 14, y + 2, panelWidth - 28,
                    EGO_ROW_HEIGHT - 4, i, false, false);
            renderFirstItem(graphics, entry, panelX + 24, y + 14);
            String type = entry.getString("Type");
            boolean equipped = entry.getBoolean("Equipped");
            int nameWidth = Math.max(40, panelWidth - 54 - 110);
            drawMarqueeText(graphics, emptyAsNone(entry.getString("DisplayName")),
                    panelX + 54, y + 9, nameWidth, LobotomyUiTheme.TEXT_TITLE);
            graphics.drawString(font, tr("weapon".equals(type)
                            ? "screen.lobotocraft.facility.personal_ego.weapon"
                            : "screen.lobotocraft.facility.personal_ego.armor"),
                    panelX + 54, y + 25, LobotomyUiTheme.TEXT_MUTED, false);
            int bx = panelX + panelWidth - 94;
            boolean hovered = inside(mouseX, mouseY, bx, y + 11, 66, 24);
            LobotomyUiRenderer.drawButton(graphics, font, bx, y + 11, 66, 24,
                    Component.translatable(equipped
                            ? "screen.lobotocraft.common.unequip"
                            : "screen.lobotocraft.common.equip"),
                    equipped && hovered ? LobotomyUiRenderer.ButtonState.DANGER
                            : equipped ? LobotomyUiRenderer.ButtonState.SELECTED
                            : hovered ? LobotomyUiRenderer.ButtonState.HOVER
                            : LobotomyUiRenderer.ButtonState.NORMAL);
        }
    }

    private void renderAiEgo(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        ListTag entries = snapshot.getList("AiEgo", Tag.TAG_COMPOUND);
        graphics.drawString(font, selectedAiEmployee == null
                        ? tr("screen.lobotocraft.facility.ai_ego.select_employee")
                        : tr("screen.lobotocraft.facility.ai_ego.target", fit(selectedAiName, 180)),
                panelX + 18, top + 2,
                selectedAiEmployee == null ? LobotomyUiTheme.ALERT_RED : LobotomyUiTheme.ACCENT, false);
        if (entries.isEmpty()) {
            drawEmpty(graphics, top + 18, tr("screen.lobotocraft.facility.ai_ego.empty"));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int y = top + 18 + i * EGO_ROW_HEIGHT - scroll;
            if (!rowVisible(y, top, EGO_ROW_HEIGHT)) continue;
            LobotomyUiRenderer.drawRow(graphics, panelX + 14, y + 2, panelWidth - 28,
                    EGO_ROW_HEIGHT - 4, i, false, false);
            renderFirstItem(graphics, entry, panelX + 24, y + 14);
            boolean compact = panelWidth < 520;
            int nameWidth = compact ? Math.max(40, panelWidth - 54 - 98) : 145;
            drawMarqueeText(graphics, emptyAsNone(entry.getString("DisplayName")),
                    panelX + 54, y + 9, nameWidth, LobotomyUiTheme.TEXT_TITLE);
            graphics.drawString(font, tr("weapon".equals(entry.getString("Type"))
                            ? "equipment.lobotocraft.weapon"
                            : "equipment.lobotocraft.armor"),
                    panelX + 54, y + 25, LobotomyUiTheme.TEXT_MUTED, false);
            boolean meetsRequirements = selectedMeetsRequirements(entry);
            String requirements = requirementText(entry);
            if (!compact) drawOwnerSlots(graphics, panelX + 205, y + 9, entry);
            int requirementX = compact ? panelX + 54 : panelX + 205;
            int requirementWidth = Math.max(40,
                    panelX + panelWidth - 92 - requirementX);
            graphics.drawString(font, fit(requirements, requirementWidth),
                    requirementX, compact ? y + 41 : y + 42,
                    selectedAiEmployee == null || meetsRequirements
                            ? LobotomyUiTheme.TEXT_MUTED : LobotomyUiTheme.ALERT_RED, false);
            int bx = panelX + panelWidth - 82;
            boolean assigned = entry.getString("Code").equals(selectedAssignedCode(entry.getString("Type")));
            boolean restricted = selectedAiEmployee != null && !meetsRequirements;
            CompoundTag selectedEmployee = selectedEmployeeEntry();
            boolean panicking = selectedEmployee != null && selectedEmployee.getBoolean("Panicking");
            boolean controlled = selectedEmployee != null
                    && "CONTROLLED".equals(selectedEmployee.getString("Status"));
            boolean enabled = snapshot.getBoolean("CanManageAi") && selectedAiEmployee != null
                    && !panicking && !controlled && meetsRequirements
                    && (entry.getInt("Available") > 0 || assigned);
            boolean hovered = enabled && inside(mouseX, mouseY, bx, y + 11, 54, 24);
            LobotomyUiRenderer.drawButton(graphics, font, bx, y + 11, 54, 24,
                    Component.translatable(assigned
                            ? "screen.lobotocraft.common.equipped"
                            : "screen.lobotocraft.common.assign"),
                    assigned ? LobotomyUiRenderer.ButtonState.SELECTED
                            : !enabled ? LobotomyUiRenderer.ButtonState.DISABLED
                            : hovered ? LobotomyUiRenderer.ButtonState.HOVER
                            : LobotomyUiRenderer.ButtonState.NORMAL);
            if (restricted && !assigned) {
                String restrictedText = tr("screen.lobotocraft.facility.ai_ego.restricted");
                int tagWidth = font.width(restrictedText) + 8;
                LobotomyUiRenderer.drawTag(graphics, font, bx + (54 - tagWidth) / 2, y + 38,
                        restrictedText, LobotomyUiTheme.ALERT_RED);
            }
        }
    }

    /**
     * 持有者槽位组 —— 还原原版 {@code Inventory/InventorySlot} 的 {@code ownerSlot} 列表：
     * 一件库存对应一格，格子三态与原版 {@code InventoryAgentSlot} 对齐。
     * <ul>
     *   <li>已占用（Assigned）：GOLD 实心格 —— 原版 {@code SetAgent(...)}</li>
     *   <li>空闲（Available）：暗底 + 亮描边空格 —— 原版 {@code SetEmpty()}</li>
     *   <li>未持有（Maximum 之内但尚未研发出）：极暗虚位 —— 原版 {@code SetDisabled()}</li>
     * </ul>
     * 格数超过 {@link #OWNER_SLOT_MAX} 时退化为「若干格 + ×N」，避免行内画爆。
     */
    private void drawOwnerSlots(GuiGraphics graphics, int x, int y, CompoundTag entry) {
        int assigned = Math.max(0, entry.getInt("Assigned"));
        int total = Math.max(0, entry.getInt("Total"));
        int maximum = Math.max(total, entry.getInt("Maximum"));
        int available = Math.max(0, entry.getInt("Available"));

        int slots = Math.min(maximum, OWNER_SLOT_MAX);
        int cursor = x;
        for (int slot = 0; slot < slots; slot++) {
            // 绘制顺序与原版一致：先已占用，再空闲，余下为未持有虚位
            if (slot < assigned) {
                graphics.fill(cursor, y, cursor + OWNER_SLOT_W, y + OWNER_SLOT_H, LobotomyUiTheme.GOLD);
            } else if (slot < assigned + available) {
                graphics.fill(cursor, y, cursor + OWNER_SLOT_W, y + OWNER_SLOT_H, LobotomyUiTheme.BG_INSET);
                drawSlotOutline(graphics, cursor, y, LobotomyUiTheme.LINE_MID);
            } else {
                graphics.fill(cursor, y, cursor + OWNER_SLOT_W, y + OWNER_SLOT_H, LobotomyUiTheme.BG_ROW_ODD);
                drawSlotOutline(graphics, cursor, y, LobotomyUiTheme.LINE_FAINT);
            }
            cursor += OWNER_SLOT_W + OWNER_SLOT_GAP;
        }
        if (maximum > OWNER_SLOT_MAX) {
            graphics.drawString(font, "×" + maximum, cursor + 1, y + 1,
                    LobotomyUiTheme.TEXT_MUTED, false);
            cursor += font.width("×" + maximum) + 5;
        }
        // 槽位下方一行明细：已分配 / 可用（可用为 0 时标红，替代原来纯数字的三段式）
        String detail = tr("screen.lobotocraft.facility.ai_ego.stock", assigned, available);
        graphics.drawString(font, detail, x, y + OWNER_SLOT_H + 3,
                available > 0 ? LobotomyUiTheme.TEXT_MUTED : LobotomyUiTheme.ALERT_RED, false);
    }

    private void drawSlotOutline(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + OWNER_SLOT_W, y + 1, color);
        graphics.fill(x, y + OWNER_SLOT_H - 1, x + OWNER_SLOT_W, y + OWNER_SLOT_H, color);
        graphics.fill(x, y + 1, x + 1, y + OWNER_SLOT_H - 1, color);
        graphics.fill(x + OWNER_SLOT_W - 1, y + 1, x + OWNER_SLOT_W, y + OWNER_SLOT_H - 1, color);
    }

    private void renderFirstItem(GuiGraphics graphics, CompoundTag entry, int x, int y) {
        ListTag items = entry.getList("Items", Tag.TAG_STRING);
        if (items.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(items.getString(0));
        if (id == null) return;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null) graphics.renderItem(new ItemStack(item), x, y);
    }

    private void drawEmpty(GuiGraphics graphics, int top, String text) {
        int centerX = panelX + panelWidth / 2;
        LobotomyUiRenderer.drawCorners(graphics, centerX - 8, top + 30, 16, 16, 5,
                LobotomyUiTheme.LINE_FAINT);
        graphics.drawString(font, text, centerX - font.width(text) / 2, top + 54,
                LobotomyUiTheme.TEXT_MUTED, false);
    }

    /** tooltip 用状态文案；与行内 FilterText 走同一套 AgentState 判定，避免两处措辞不一致。 */
    private String statusText(CompoundTag entry) {
        AgentState state = agentState(entry, "AI_EMPLOYEE".equals(entry.getString("ActorType")));
        return tr(state.translationKey);
    }

    private String selectedAssignedCode(String equipmentType) {
        if (selectedAiEmployee == null) return "";
        ListTag employees = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int i = 0; i < employees.size(); i++) {
            CompoundTag entry = employees.getCompound(i);
            if (entry.hasUUID("Id") && selectedAiEmployee.equals(entry.getUUID("Id"))) {
                return entry.getString("weapon".equals(equipmentType) ? "WeaponEgo" : "ArmorEgo");
            }
        }
        return "";
    }

    private CompoundTag selectedEmployeeEntry() {
        if (selectedAiEmployee == null) return null;
        ListTag employees = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int index = 0; index < employees.size(); index++) {
            CompoundTag entry = employees.getCompound(index);
            if (entry.hasUUID("Id") && selectedAiEmployee.equals(entry.getUUID("Id"))) return entry;
        }
        return null;
    }

    private boolean selectedMeetsRequirements(CompoundTag ego) {
        CompoundTag employee = selectedEmployeeEntry();
        if (employee == null) return false;
        for (String key : new String[]{"FortitudeLevel", "PrudenceLevel", "TemperanceLevel",
                "JusticeLevel", "EmployeeLevel"}) {
            int required = ego.getInt("Required" + key);
            int actual = employee.getInt(key);
            if (!"EmployeeLevel".equals(key) && key.endsWith("Level")) {
                String rawKey = key.substring(0, key.length() - "Level".length());
                if (employee.contains(rawKey)) {
                    actual = com.wzz.lobotocraft.capability.EmployeeLevelCalculator
                            .levelForEquipmentRequirement(
                                    employee.getInt(rawKey), actual, required);
                }
            }
            if (actual < required) return false;
        }
        return true;
    }

    private String requirementText(CompoundTag ego) {
        java.util.List<String> requirements = new java.util.ArrayList<>();
        addRequirement(requirements, ego, "FortitudeLevel", "stat.lobotocraft.fortitude");
        addRequirement(requirements, ego, "PrudenceLevel", "stat.lobotocraft.prudence");
        addRequirement(requirements, ego, "TemperanceLevel", "stat.lobotocraft.temperance");
        addRequirement(requirements, ego, "JusticeLevel", "stat.lobotocraft.justice");
        addRequirement(requirements, ego, "EmployeeLevel", "stat.lobotocraft.employee_level");
        return requirements.isEmpty()
                ? tr("screen.lobotocraft.ego.no_level_requirement")
                : tr("screen.lobotocraft.ego.requirements", String.join(" · ", requirements));
    }

    private void addRequirement(java.util.List<String> target, CompoundTag ego,
                                String key, String displayNameKey) {
        int required = ego.getInt("Required" + key);
        if (required <= 0) return;
        target.add(tr("screen.lobotocraft.ego.requirement_entry",
                tr(displayNameKey), roman(required)));
    }

    private static String roman(int level) {
        return com.wzz.lobotocraft.capability.EmployeeLevelCalculator.levelGrade(level);
    }

    private void restoreEmployeeSelection() {
        if (rememberedAiEmployee == null) return;
        ListTag employees = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int index = 0; index < employees.size(); index++) {
            CompoundTag entry = employees.getCompound(index);
            if (entry.hasUUID("Id") && rememberedAiEmployee.equals(entry.getUUID("Id"))
                    && "AI_EMPLOYEE".equals(entry.getString("ActorType"))) {
                selectedAiEmployee = rememberedAiEmployee;
                selectedAiName = entry.getString("Name");
                return;
            }
        }
        rememberedAiEmployee = null;
    }

    private void openDepartmentScreen() {
        if (!hasAiEmployees()) return;
        minecraft.setScreen(new DepartmentAssignmentScreen(snapshot, selectedAiEmployee));
    }

    private void openAppearanceScreen() {
        if (!canEditSelectedAppearance()) return;
        EmployeeAppearance current = selectedAppearance();
        String provider = snapshot.getString("YsmProvider");
        if (provider.isBlank() && !current.isDefault()) provider = current.provider();
        YsmModelCatalog catalog = YsmModelCatalog.readFrom(snapshot);
        String selectedProvider = provider;
        minecraft.setScreen(new YsmEmployeeModelScreen(this, selectedProvider, catalog, current,
                selected -> {
                    MessageLoader.getLoader().sendToServer(
                            new SetEmployeeAppearancePacket(selectedAiEmployee, selected));
                    minecraft.setScreen(this);
                }));
    }

    private boolean canEditSelectedAppearance() {
        if (!snapshot.getBoolean("CanManageAi") || selectedAiEmployee == null) return false;
        EmployeeAppearance current = selectedAppearance();
        String provider = snapshot.getString("YsmProvider");
        YsmModelCatalog catalog = YsmModelCatalog.readFrom(snapshot);
        boolean catalogUsable = snapshot.getBoolean("YsmAvailable")
                && YsmClientCompat.isAvailable(provider)
                && (!catalog.authoritative() || !catalog.keys().isEmpty());
        return catalogUsable || !current.isDefault();
    }

    private EmployeeAppearance selectedAppearance() {
        CompoundTag employee = selectedEmployeeEntry();
        return employee == null ? EmployeeAppearance.DEFAULT
                : EmployeeAppearance.load(employee.getCompound("Appearance"));
    }

    private boolean hasAiEmployees() {
        ListTag employees = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int index = 0; index < employees.size(); index++) {
            if ("AI_EMPLOYEE".equals(employees.getCompound(index).getString("ActorType"))) return true;
        }
        return false;
    }

    private String departmentId(String displayName) {
        for (com.wzz.lobotocraft.employee.EmployeeDepartment department
                : com.wzz.lobotocraft.employee.EmployeeDepartment.values()) {
            if (department.displayName().equals(displayName)) return department.id();
        }
        return "unassigned";
    }

    private void renderContextTooltip(GuiGraphics graphics, int mouseX, int mouseY, int top) {
        if (mouseY < top || mouseY >= panelY + panelHeight - 14) return;
        int rowTop = activeTab == 2 ? top + 18 : top;
        int rowHeight = activeTab == 0 ? EMPLOYEE_ROW_HEIGHT : EGO_ROW_HEIGHT;
        int row = Mth.floor((mouseY - rowTop + scroll) / rowHeight);
        ListTag entries = snapshot.getList(activeTab == 0 ? "Employees" : activeTab == 1 ? "PersonalEgo" : "AiEgo", Tag.TAG_COMPOUND);
        if (row < 0 || row >= entries.size()) return;
        CompoundTag entry = entries.getCompound(row);
        if (activeTab == 0 && inside(mouseX, mouseY, panelX + 14,
                rowTop + row * rowHeight - scroll + 2, panelWidth - 28, rowHeight - 4)) {
            String detail = "AI_EMPLOYEE".equals(entry.getString("ActorType"))
                    ? tr("screen.lobotocraft.facility.employee.tooltip",
                    departmentName(entry), statusText(entry),
                    Math.round(entry.getFloat("Health")), Math.round(entry.getFloat("MaxHealth")),
                    Math.round(entry.getFloat("Mental")), Math.round(entry.getFloat("MaxMental")),
                    egoName(entry, "Weapon"), egoName(entry, "Armor"))
                    : tr("screen.lobotocraft.facility.employee.player_uncommandable");
            graphics.renderTooltip(font, Component.literal(detail), mouseX, mouseY);
        } else if (activeTab != 0 && mouseX >= panelX + 18 && mouseX < panelX + panelWidth - 14) {
            String detail = tr("screen.lobotocraft.facility.ego.tooltip",
                    emptyAsNone(entry.getString("DisplayName")),
                    tr("weapon".equals(entry.getString("Type"))
                            ? "equipment.lobotocraft.ego_weapon"
                            : "equipment.lobotocraft.ego_armor"));
            if (activeTab == 2) detail = tr("screen.lobotocraft.facility.ego.tooltip_requirements",
                    detail, requirementText(entry));
            graphics.renderTooltip(font, Component.literal(detail), mouseX, mouseY);
        }
    }

    private String emptyAsNone(String value) {
        return value == null || value.isBlank() ? tr("screen.lobotocraft.common.none") : value;
    }

    private String egoName(CompoundTag entry, String prefix) {
        String key = entry.getString(prefix + "EgoNameKey");
        if (!key.isBlank()) return tr(key);
        return emptyAsNone(entry.getString(prefix + "EgoName"));
    }

    private String departmentName(CompoundTag entry) {
        String id = entry.getString("DepartmentId");
        if (id.isBlank()) id = departmentId(entry.getString("Department"));
        return tr("department.lobotocraft." + id);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean rowVisible(int y, int top, int rowHeight) {
        return y + rowHeight > top && y < panelY + panelHeight - 14;
    }

    private String fit(String value, int maxWidth) {
        return LobotomyUiRenderer.fitText(font, value, maxWidth);
    }

    private void drawMarqueeText(GuiGraphics graphics, String text, int x, int y,
                                  int maxWidth, int color) {
        if (maxWidth <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        int overflow = textWidth - maxWidth;
        long pauseMs = 650L;
        long moveMs = Math.max(450L, overflow * 32L);
        long phase = Util.getMillis() % (pauseMs * 2L + moveMs * 2L);
        float offset;
        if (phase < pauseMs) offset = 0.0F;
        else if (phase < pauseMs + moveMs) {
            offset = overflow * (phase - pauseMs) / (float) moveMs;
        } else if (phase < pauseMs * 2L + moveMs) offset = overflow;
        else {
            offset = overflow * (1.0F
                    - (phase - pauseMs * 2L - moveMs) / (float) moveMs);
        }
        graphics.enableScissor(x, y, x + maxWidth, y + font.lineHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(-offset, 0.0F, 1.0F);
        graphics.drawString(font, text, x, y, color, false);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int clickedTab = tabIndexAt(mouseX, mouseY);
        if (clickedTab >= 0) {
            if (clickedTab == 2) {
                minecraft.setScreen(new EgoAssignmentScreen(snapshot, this));
                return true;
            }
            activeTab = clickedTab;
            scroll = 0;
            return true;
        }
        if (activeTab == 1) {
            int top = panelY + HEADER_HEIGHT + 30;
            int row = Mth.floor((mouseY - top + scroll) / EGO_ROW_HEIGHT);
            ListTag entries = snapshot.getList("PersonalEgo", Tag.TAG_COMPOUND);
            int bx = panelX + panelWidth - 94;
            int y = top + row * EGO_ROW_HEIGHT - scroll;
            if (row >= 0 && row < entries.size() && inside(mouseX, mouseY, bx, y + 11, 66, 24)) {
                CompoundTag entry = entries.getCompound(row);
                MessageLoader.getLoader().sendToServer(new EquipPersonalEgoPacket(
                        entry.getBoolean("Equipped") ? "" : entry.getString("Code"),
                        entry.getString("Type")));
                return true;
            }
        }
        if (activeTab == 0) {
            int top = panelY + HEADER_HEIGHT + 30;
            int row = Mth.floor((mouseY - top + scroll) / EMPLOYEE_ROW_HEIGHT);
            ListTag entries = snapshot.getList("Employees", Tag.TAG_COMPOUND);
            if (row >= 0 && row < entries.size()) {
                CompoundTag entry = entries.getCompound(row);
                if ("AI_EMPLOYEE".equals(entry.getString("ActorType")) && entry.hasUUID("Id")) {
                    selectedAiEmployee = entry.getUUID("Id");
                    selectedAiName = entry.getString("Name");
                    rememberedAiEmployee = selectedAiEmployee;
                    return true;
                }
            }
        }
        if (activeTab == 2 && snapshot.getBoolean("CanManageAi") && selectedAiEmployee != null) {
            CompoundTag selectedEmployee = selectedEmployeeEntry();
            if (selectedEmployee == null || selectedEmployee.getBoolean("Panicking")
                    || "CONTROLLED".equals(selectedEmployee.getString("Status"))) return true;
            int top = panelY + HEADER_HEIGHT + 48;
            int row = Mth.floor((mouseY - top + scroll) / EGO_ROW_HEIGHT);
            ListTag entries = snapshot.getList("AiEgo", Tag.TAG_COMPOUND);
            int bx = panelX + panelWidth - 82;
            int y = top + row * EGO_ROW_HEIGHT - scroll;
            if (row >= 0 && row < entries.size() && inside(mouseX, mouseY, bx, y + 11, 54, 24)) {
                CompoundTag entry = entries.getCompound(row);
                boolean assigned = entry.getString("Code").equals(selectedAssignedCode(entry.getString("Type")));
                if (selectedMeetsRequirements(entry) && (entry.getInt("Available") > 0 || assigned)) {
                    MessageLoader.getLoader().sendToServer(new AssignAiEgoPacket(selectedAiEmployee,
                            entry.getString("Code"), entry.getString("Type")));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int count = activeTab == 0 ? snapshot.getList("Employees", Tag.TAG_COMPOUND).size()
                : activeTab == 1 ? snapshot.getList("PersonalEgo", Tag.TAG_COMPOUND).size()
                : snapshot.getList("AiEgo", Tag.TAG_COMPOUND).size();
        int visibleHeight = panelHeight - HEADER_HEIGHT - 44 - (activeTab == 2 ? 18 : 0);
        int rowHeight = activeTab == 0 ? EMPLOYEE_ROW_HEIGHT : EGO_ROW_HEIGHT;
        int maximum = Math.max(0, count * rowHeight - visibleHeight);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * rowHeight, 0, maximum);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
                && snapshot.getBoolean("CanPause")
                && FacilityPauseButton.canToggle()) {
            FacilityPauseButton.requestToggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
