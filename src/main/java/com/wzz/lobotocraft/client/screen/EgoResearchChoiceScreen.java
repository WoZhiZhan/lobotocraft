package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.ego.EgoResearchScope;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EgoResearchChoiceScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 188;
    private static final int CARD_WIDTH = 142;
    private static final int CARD_HEIGHT = 48;

    private final String equipmentType;
    private final String abnormalityCode;
    private final int abnormalityId;
    private final double scrollOffset;
    private final IAbnormality abnormality;
    private final int cost;
    private final Screen returnScreen;

    public EgoResearchChoiceScreen(String equipmentType, String abnormalityCode,
                                   int abnormalityId, double scrollOffset,
                                   IAbnormality abnormality, int cost) {
        this(equipmentType, abnormalityCode, abnormalityId, scrollOffset,
                abnormality, cost, null);
    }

    public EgoResearchChoiceScreen(String equipmentType, String abnormalityCode,
                                   int abnormalityId, double scrollOffset,
                                   IAbnormality abnormality, int cost,
                                   Screen returnScreen) {
        super(Component.translatable("screen.lobotocraft.ego_research.title"));
        this.equipmentType = equipmentType;
        this.abnormalityCode = abnormalityCode;
        this.abnormalityId = abnormalityId;
        this.scrollOffset = scrollOffset;
        this.abnormality = abnormality;
        this.cost = cost;
        this.returnScreen = returnScreen;
    }

    @Override
    public void init() {
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(LobotomyButton.create(Component.translatable(
                        "screen.lobotocraft.common.cancel"), button -> onClose())
                .bounds(x + 120, y + 145, 120, 22).build());
    }

    private void start(EgoResearchScope scope) {
        if (minecraft == null) return;
        minecraft.setScreen(new EquipmentCraftingScreen(equipmentType, abnormalityCode,
                abnormalityId, scrollOffset, abnormality, cost, scope,
                rootReturnScreen()));
    }

    private Screen rootReturnScreen() {
        if (returnScreen instanceof AbnormalityEncyclopediaScreen encyclopedia) {
            return encyclopedia.getReturnScreen();
        }
        return returnScreen;
    }

    @Override
    public void onClose() {
        if (returnScreen != null && minecraft != null) {
            minecraft.setScreen(returnScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        LobotomyUiRenderer.drawPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, x, y, PANEL_WIDTH,
                "E.G.O DEVELOPMENT", abnormalityCode + " / "
                        + tr("weapon".equals(equipmentType)
                        ? "equipment.lobotocraft.weapon" : "equipment.lobotocraft.armor"));

        renderScopeCard(graphics, x + 28, y + 56,
                tr("screen.lobotocraft.ego_research.personal"),
                tr("screen.lobotocraft.ego_research.personal.subtitle"),
                LobotomyUiTheme.GOLD, isMouseOverCard(mouseX, mouseY, x + 28, y + 56));
        renderScopeCard(graphics, x + 190, y + 56,
                tr("screen.lobotocraft.ego_research.ai_global"),
                tr("screen.lobotocraft.ego_research.ai_global.subtitle"),
                LobotomyUiTheme.ACCENT, isMouseOverCard(mouseX, mouseY, x + 190, y + 56));

        // 两卡之间的竖向分隔，强调这是两套彼此独立的额度而非同一池子的两种用法
        int dividerX = x + 28 + CARD_WIDTH + (190 - 28 - CARD_WIDTH) / 2;
        graphics.fill(dividerX, y + 60, dividerX + 1, y + 56 + CARD_HEIGHT - 4,
                LobotomyUiTheme.LINE_FAINT);

        graphics.drawString(font, tr("screen.lobotocraft.ego_research.personal.description"),
                x + 28, y + 112, LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, tr("screen.lobotocraft.ego_research.ai_global.description"),
                x + 28, y + 124, LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, tr("screen.lobotocraft.ego_research.independent"),
                x + 28, y + 136, LobotomyUiTheme.ACCENT_DIM, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 归属选项卡片：BG_INSET 底 + 悬停青白描边与角括号 */
    private void renderScopeCard(GuiGraphics graphics, int x, int y,
                                 String title, String subtitle, int titleColor, boolean hovered) {
        LobotomyUiRenderer.drawInsetBox(graphics, x, y, CARD_WIDTH, CARD_HEIGHT);
        if (hovered) {
            graphics.fill(x, y, x + CARD_WIDTH, y + 1, LobotomyUiTheme.LINE_ACCENT);
            graphics.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT,
                    LobotomyUiTheme.LINE_ACCENT);
            graphics.fill(x, y, x + 1, y + CARD_HEIGHT, LobotomyUiTheme.LINE_ACCENT);
            graphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT,
                    LobotomyUiTheme.LINE_ACCENT);
            LobotomyUiRenderer.drawCorners(graphics, x, y, CARD_WIDTH, CARD_HEIGHT,
                    4, LobotomyUiTheme.LINE_ACCENT);
        }
        graphics.drawString(font, title, x + 10, y + 10, titleColor, false);
        graphics.drawString(font, subtitle, x + 10, y + 28, LobotomyUiTheme.TEXT_MUTED, false);
    }

    private boolean isMouseOverCard(double mouseX, double mouseY, int cardX, int cardY) {
        return mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            int x = (width - PANEL_WIDTH) / 2;
            int y = (height - PANEL_HEIGHT) / 2;
            if (isMouseOverCard(mouseX, mouseY, x + 28, y + 56)) {
                start(EgoResearchScope.PERSONAL);
                return true;
            }
            if (isMouseOverCard(mouseX, mouseY, x + 190, y + 56)) {
                start(EgoResearchScope.AI_GLOBAL);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
