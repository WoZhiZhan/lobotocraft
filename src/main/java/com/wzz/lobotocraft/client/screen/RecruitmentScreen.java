package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.compat.YsmClientCompat;
import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import com.wzz.lobotocraft.compat.ysm.YsmModelCatalog;
import com.wzz.lobotocraft.employee.RecruitmentService;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RecruitEmployeePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RecruitmentScreen extends Screen {
    private static final String[] STAT_NAMES = {"勇气", "谨慎", "自律", "正义"};
    private static final String[] LEVELS = {"I", "II", "III", "IV", "V"};
    private static final int[] STAT_COLORS = {LobotomyUiTheme.STAT_FORTITUDE, LobotomyUiTheme.STAT_PRUDENCE,
            LobotomyUiTheme.STAT_TEMPERANCE, LobotomyUiTheme.STAT_JUSTICE};
    private static final ResourceLocation[] STAT_ICONS = {
            new ResourceLocation("lobotocraft", "textures/gui/theme/stat_fortitude.png"),
            new ResourceLocation("lobotocraft", "textures/gui/theme/stat_prudence.png"),
            new ResourceLocation("lobotocraft", "textures/gui/theme/stat_temperance.png"),
            new ResourceLocation("lobotocraft", "textures/gui/theme/stat_justice.png")};
    private static final ResourceLocation PE_BOX_ICON =
            new ResourceLocation("lobotocraft", "textures/gui/pe_box_storage.png");
    private static final int PANEL_HEIGHT = 322;
    private final Screen parent;
    private final CompoundTag resources;
    private final YsmModelCatalog ysmCatalog;
    private final int[] levels = {1, 1, 1, 1};
    private final Button[] minusButtons = new Button[4];
    private final Button[] plusButtons = new Button[4];
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean compact;
    private String nameDraft = "";
    private String personaDraft = "";
    private EmployeeAppearance appearance = EmployeeAppearance.DEFAULT;
    private EditBox nameBox;
    private MultiLineEditBox personaBox;
    private Button recruitButton;
    private boolean ysmSelectable;
    private String ysmProvider = "";
    private int nameFrameWidth;
    private int appearanceButtonX;
    private int appearanceButtonWidth;

    public RecruitmentScreen(Screen parent, CompoundTag resources) {
        super(Component.literal("员工招募"));
        this.parent = parent;
        this.resources = resources.copy();
        this.ysmCatalog = YsmModelCatalog.readFrom(this.resources);
    }

    @Override
    public void init() {
        if (nameBox != null) nameDraft = nameBox.getValue();
        if (personaBox != null) personaDraft = personaBox.getValue();
        panelWidth = Math.min(520, width - 16);
        panelHeight = Math.min(PANEL_HEIGHT, height - 8);
        compact = panelHeight < PANEL_HEIGHT;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, (height - panelHeight) / 2);
        int contentX = panelX + 24;
        int nameBoxY = panelY + (compact ? 48 : 55);
        int statTop = panelY + (compact ? 72 : 91);
        int statStep = compact ? 23 : 29;
        ysmProvider = resources.getString("YsmProvider");
        ysmSelectable = resources.getBoolean("YsmAvailable")
                && YsmClientCompat.isAvailable(ysmProvider)
                && (!ysmCatalog.authoritative() || !ysmCatalog.keys().isEmpty());
        if (!ysmSelectable) appearance = EmployeeAppearance.DEFAULT;
        int nameRowWidth = panelWidth - 48;
        appearanceButtonWidth = ysmSelectable
                ? Math.min(180, Math.max(104, nameRowWidth / 3)) : 0;
        nameFrameWidth = ysmSelectable ? nameRowWidth - appearanceButtonWidth - 8 : nameRowWidth;
        appearanceButtonX = panelX + 24 + nameFrameWidth + 8;

        nameBox = addRenderableWidget(new EditBox(font, contentX + 5, nameBoxY + 7,
                nameFrameWidth - 10, 12, Component.literal("员工名称")));
        nameBox.setBordered(false);
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.literal("员工名称"));
        nameBox.setValue(nameDraft);
        nameBox.setResponder(value -> {
            nameDraft = value;
            updateControls();
        });

        if (ysmSelectable) {
            Button appearanceButton = addRenderableWidget(LobotomyButton.create(
                    Component.literal(appearanceButtonText()), button -> minecraft.setScreen(
                            new YsmEmployeeModelScreen(this, ysmProvider, ysmCatalog,
                                    appearance, selected -> {
                                appearance = selected;
                                minecraft.setScreen(this);
                            })))
                    .bounds(appearanceButtonX, nameBoxY, appearanceButtonWidth, 22)
                    .tooltip(Tooltip.create(Component.literal(appearanceTooltip())))
                    .build());
            appearanceButton.active = true;
        }

        int statsWidth = (panelWidth - 56) / 2;
        int personaX = panelX + 34 + statsWidth;
        personaBox = addRenderableWidget(new MultiLineEditBox(font, personaX, statTop + 14,
                panelX + panelWidth - 24 - personaX, compact ? 75 : 88,
                Component.literal("例如：沉稳寡言，但会照顾新人"), Component.literal("员工人设（可选）")));
        personaBox.setCharacterLimit(RecruitmentService.MAX_PERSONA_UTF16_LENGTH);
        personaBox.setValue(personaDraft);
        personaBox.setValueListener(value -> {
            personaDraft = value;
            updateControls();
        });

        for (int index = 0; index < 4; index++) {
            int rowY = statTop + index * statStep;
            final int stat = index;
            minusButtons[index] = addRenderableWidget(LobotomyButton.create(Component.literal("-"), button -> {
                levels[stat] = Math.max(1, levels[stat] - 1);
                updateControls();
            }).bounds(panelX + 22 + statsWidth - 72, rowY, 24, 20).build());
            plusButtons[index] = addRenderableWidget(LobotomyButton.create(Component.literal("+"), button -> {
                levels[stat] = Math.min(5, levels[stat] + 1);
                updateControls();
            }).bounds(panelX + 22 + statsWidth - 24, rowY, 24, 20).build());
        }

        recruitButton = addRenderableWidget(LobotomyButton.create(Component.literal("确认招募"), button -> recruit())
                .bounds(panelX + panelWidth / 2 - 104,
                        panelY + panelHeight - (compact ? 28 : 36), 96, 22).build());
        addRenderableWidget(LobotomyButton.create(Component.literal("返回"), button -> onClose())
                .bounds(panelX + panelWidth / 2 + 8,
                        panelY + panelHeight - (compact ? 28 : 36), 96, 22).build());
        setInitialFocus(nameBox);
        updateControls();
    }

    private void recruit() {
        if (!recruitButton.active) return;
        MessageLoader.getLoader().sendToServer(new RecruitEmployeePacket(nameBox.getValue(),
                levels[0], levels[1], levels[2], levels[3], personaForSubmission(), appearance));
        recruitButton.active = false;
        recruitButton.setMessage(Component.literal("处理中…"));
    }

    private void updateControls() {
        for (int index = 0; index < 4; index++) {
            minusButtons[index].active = levels[index] > 1;
            plusButtons[index].active = levels[index] < 5;
            plusButtons[index].setTooltip(levels[index] < 5
                    ? Tooltip.create(Component.literal(nextUpgradeCost(levels[index]))) : null);
        }
        if (recruitButton != null) {
            RecruitmentService.Cost cost = cost();
            boolean affordable = canAfford(cost);
            recruitButton.active = validName() && validPersona() && affordable;
            recruitButton.setMessage(Component.literal("确认招募"));
            if (!validName()) {
                recruitButton.setTooltip(Tooltip.create(Component.literal("请输入有效的员工名称")));
            } else if (!validPersona()) {
                recruitButton.setTooltip(Tooltip.create(Component.literal("人设最多256个字符，且不能包含§")));
            } else if (!affordable) {
                recruitButton.setTooltip(Tooltip.create(Component.literal("资源不足：" + shortfall(cost))));
            } else {
                recruitButton.setTooltip(null);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "EMPLOYEE RECRUITMENT", "员工招募 / 初始能力配置");

        graphics.drawString(font, "名称", panelX + 24, panelY + (compact ? 38 : 45), LobotomyUiTheme.TEXT_MUTED, false);
        int boxX = panelX + 24;
        int boxY = panelY + (compact ? 48 : 55);
        LobotomyUiRenderer.drawInsetBox(graphics, boxX, boxY, nameFrameWidth, 22);
        drawFrame(graphics, boxX, boxY, nameFrameWidth, 22, nameBox != null && nameBox.isFocused()
                ? LobotomyUiTheme.LINE_ACCENT : LobotomyUiTheme.LINE_MID);
        if (ysmSelectable) {
            graphics.drawString(font, "YSM 外观（可选）", appearanceButtonX,
                    panelY + (compact ? 38 : 45), LobotomyUiTheme.TEXT_MUTED, false);
        }

        int statTop = panelY + (compact ? 72 : 91);
        int statStep = compact ? 23 : 29;
        for (int index = 0; index < 4; index++) {
            int rowY = statTop + index * statStep;
            int statsWidth = (panelWidth - 56) / 2;
            LobotomyUiRenderer.drawRow(graphics, panelX + 22, rowY - 3, statsWidth, 26,
                    index, false, false);
            drawStatIcon(graphics, index, panelX + 26, rowY + 6);
            graphics.drawString(font, STAT_NAMES[index], panelX + 38, rowY + 6,
                    LobotomyUiTheme.TEXT_TITLE, false);
            LobotomyUiRenderer.drawInsetBox(graphics, panelX + 22 + statsWidth - 48, rowY, 24, 20);
            graphics.drawCenteredString(font, LEVELS[levels[index] - 1],
                    panelX + 22 + statsWidth - 36, rowY + 6, LobotomyUiTheme.GOLD);
        }

        int statsWidth = (panelWidth - 56) / 2;
        int personaX = panelX + 34 + statsWidth;
        String personaCount = personaCodePointCount() + " / " + RecruitmentService.MAX_PERSONA_CODE_POINTS;
        graphics.drawString(font, "员工人设（可选）", personaX, statTop,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, personaCount, panelX + panelWidth - 24 - font.width(personaCount),
                statTop, validPersona() ? LobotomyUiTheme.TEXT_MUTED : LobotomyUiTheme.ALERT_RED, false);

        RecruitmentService.Cost cost = cost();
        int costTop = panelY + (compact ? 169 : 214);
        graphics.drawString(font, "招募成本", panelX + 24, costTop, LobotomyUiTheme.TEXT_MUTED, false);
        int itemWidth = Math.max(58, (panelWidth - 48) / 5);
        drawCost(graphics, panelX + 24, costTop + 15, itemWidth, Items.EMERALD,
                cost.emeralds(), resources.getInt("RecruitEmeralds"));
        drawCost(graphics, panelX + 24 + itemWidth, costTop + 15, itemWidth, null,
                cost.peBoxes(), resources.getInt("RecruitPeBoxes"));
        drawCost(graphics, panelX + 24 + itemWidth * 2, costTop + 15, itemWidth, Items.IRON_INGOT,
                cost.iron(), resources.getInt("RecruitIron"));
        drawCost(graphics, panelX + 24 + itemWidth * 3, costTop + 15, itemWidth, Items.GOLD_INGOT,
                cost.gold(), resources.getInt("RecruitGold"));
        drawCost(graphics, panelX + 24 + itemWidth * 4, costTop + 15, itemWidth, Items.DIAMOND,
                cost.diamonds(), resources.getInt("RecruitDiamonds"));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCost(GuiGraphics graphics, int x, int y, int width, Item item,
                          int required, int held) {
        LobotomyUiRenderer.drawInsetBox(graphics, x, y, width - 4, 39);
        if (item != null) {
            graphics.renderItem(new ItemStack(item), x + 6, y + 4);
        } else if (LobotomyUiRenderer.textureExists(PE_BOX_ICON)) {
            LobotomyUiRenderer.blitScaled(graphics, PE_BOX_ICON, x + 6, y + 4, 16, 16, 16, 16);
        } else {
            graphics.drawString(font, "PE", x + 7, y + 8, LobotomyUiTheme.GOLD, false);
        }
        boolean enough = held >= required;
        int color = enough ? LobotomyUiTheme.ENERGY_GREEN : LobotomyUiTheme.ALERT_RED;
        graphics.drawString(font, held + " / " + required, x + 27, y + 8, color, false);
        LobotomyUiRenderer.drawBar(graphics, x + 5, y + 28, width - 14, 3,
                required == 0 ? 1 : held, required == 0 ? 1 : required, color);
    }

    private void drawStatIcon(GuiGraphics graphics, int index, int x, int y) {
        if (LobotomyUiRenderer.textureExists(STAT_ICONS[index])) {
            LobotomyUiRenderer.blitScaled(graphics, STAT_ICONS[index], x, y, 8, 8, 16, 16);
            return;
        }
        graphics.fill(x, y, x + 8, y + 8, STAT_COLORS[index]);
        graphics.fill(x, y, x + 8, y + 1, 0x40FFFFFF);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private String shortfall(RecruitmentService.Cost cost) {
        StringBuilder builder = new StringBuilder();
        appendShortfall(builder, "绿宝石", cost.emeralds(), resources.getInt("RecruitEmeralds"));
        appendShortfall(builder, "PE-BOX", cost.peBoxes(), resources.getInt("RecruitPeBoxes"));
        appendShortfall(builder, "铁锭", cost.iron(), resources.getInt("RecruitIron"));
        appendShortfall(builder, "金锭", cost.gold(), resources.getInt("RecruitGold"));
        appendShortfall(builder, "钻石", cost.diamonds(), resources.getInt("RecruitDiamonds"));
        return builder.toString();
    }

    private static void appendShortfall(StringBuilder builder, String name, int required, int held) {
        if (held >= required) return;
        if (builder.length() > 0) builder.append("、");
        builder.append(name).append(" x").append(required - held);
    }

    private RecruitmentService.Cost cost() {
        return RecruitmentService.cost(levels[0], levels[1], levels[2], levels[3]);
    }

    private boolean canAfford(RecruitmentService.Cost cost) {
        return resources.getInt("RecruitEmeralds") >= cost.emeralds()
                && resources.getInt("RecruitPeBoxes") >= cost.peBoxes()
                && resources.getInt("RecruitIron") >= cost.iron()
                && resources.getInt("RecruitGold") >= cost.gold()
                && resources.getInt("RecruitDiamonds") >= cost.diamonds();
    }

    private boolean validName() {
        String name = nameBox == null ? "" : nameBox.getValue().strip();
        return !name.isEmpty() && name.codePointCount(0, name.length()) <= 24
                && name.codePoints().noneMatch(character -> Character.isISOControl(character)
                || character == '\u00a7');
    }

    private boolean validPersona() {
        String persona = personaForSubmission();
        return persona.codePointCount(0, persona.length()) <= RecruitmentService.MAX_PERSONA_CODE_POINTS
                && persona.codePoints().noneMatch(character -> Character.isISOControl(character)
                || character == '\u00a7');
    }

    private int personaCodePointCount() {
        String persona = personaForSubmission();
        return persona.codePointCount(0, persona.length());
    }

    private String personaForSubmission() {
        if (personaBox == null) return "";
        return personaBox.getValue().replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').strip();
    }

    private String appearanceButtonText() {
        String text = appearance.isDefault() ? "原版外观" : "YSM: " + displayModelPath();
        if (font == null || font.width(text) <= appearanceButtonWidth - 12) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix)
                > appearanceButtonWidth - 12) end--;
        return text.substring(0, end) + suffix;
    }

    private String appearanceTooltip() {
        return appearance.isDefault() ? "保持原版员工外观" : appearance.modelPath()
                + "\n贴图：" + appearance.textureId();
    }

    private String displayModelPath() {
        String path = appearance.modelPath();
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static String nextUpgradeCost(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> "II级：10 任意PE-BOX";
            case 2 -> "III级：2 铁锭";
            case 3 -> "IV级：4 金锭";
            case 4 -> "V级：5 钻石";
            default -> "已达最高等级";
        };
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
