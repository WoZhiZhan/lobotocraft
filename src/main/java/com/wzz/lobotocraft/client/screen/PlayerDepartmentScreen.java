package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.SetPlayerDepartmentPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Player identity selection. It intentionally has no relationship to AI capacity. */
public final class PlayerDepartmentScreen extends Screen {
    private static final int PANEL_MAX_W = 780;
    private static final int PANEL_MAX_H = 380;
    private static final int GRID_COLUMNS = 5;
    private static final int CARD_GAP = 7;
    private static final int CARD_H = 116;

    private final Screen parent;
    private final List<Card> cards = new ArrayList<>();
    private CompoundTag snapshot;
    private String selectedId;
    private boolean waiting;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private LobotomyButton confirmButton;

    public PlayerDepartmentScreen(CompoundTag snapshot, Screen parent) {
        super(Component.translatable("screen.lobotocraft.player_department.title"));
        this.snapshot = snapshot.copy();
        this.parent = parent;
        this.selectedId = snapshot.getString("CurrentDepartment");
    }

    public void updateSnapshot(CompoundTag updated) {
        snapshot = updated.copy();
        selectedId = snapshot.getString("CurrentDepartment");
        waiting = false;
        clearWidgets();
        init();
    }

    @Override
    public void init() {
        updateLayout();
        int right = panelX + panelW - 16;
        LobotomyButton close = LobotomyButton.create(Component.literal("X"), button -> onClose())
                .bounds(right - 20, panelY + 12, 20, 20).build();
        close.setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.player_department.back")));
        addRenderableWidget(close);

        confirmButton = LobotomyButton.create(Component.translatable(
                "screen.lobotocraft.player_department.confirm"), button -> confirm())
                .bounds(right - 92, panelY + panelH - 32, 92, 20).build();
        confirmButton.setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.player_department.confirm.tooltip")));
        addRenderableWidget(confirmButton);
        rebuildCards();
        updateButtonState();
    }

    private void updateLayout() {
        panelW = Math.max(1, Math.min(PANEL_MAX_W, width - 12));
        panelH = Math.max(1, Math.min(PANEL_MAX_H, height - 12));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    private void rebuildCards() {
        cards.clear();
        ListTag departments = snapshot.getList("Departments", Tag.TAG_COMPOUND);
        int gridX = panelX + 16;
        int gridY = panelY + 66;
        int gridW = panelW - 32;
        int cardW = Math.max(20,
                (gridW - CARD_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS);
        int cardH = Math.min(CARD_H,
                Math.max(48, (panelH - 66 - 47 - CARD_GAP) / 2));
        for (int index = 0; index < departments.size(); index++) {
            CompoundTag department = departments.getCompound(index);
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            cards.add(new Card(department.copy(),
                    gridX + column * (cardW + CARD_GAP),
                    gridY + row * (cardH + CARD_GAP), cardW, cardH));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, LobotomyUiTheme.BG_SCRIM);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelW, panelH);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelW,
                "PERSONNEL IDENTITY", tr("screen.lobotocraft.player_department.subtitle"));

        EmployeeDepartment current = EmployeeDepartment.byId(
                snapshot.getString("CurrentDepartment"));
        String currentText = current == EmployeeDepartment.UNASSIGNED
                ? tr("screen.lobotocraft.player_department.current_none")
                : tr("screen.lobotocraft.player_department.current", departmentName(current));
        int currentColor = current == EmployeeDepartment.UNASSIGNED
                ? LobotomyUiTheme.TEXT_MUTED : 0xFF000000 | current.textColorRgb();
        LobotomyUiRenderer.drawTag(graphics, font, panelX + 16, panelY + 46,
                currentText, currentColor);

        for (Card card : cards) renderCard(graphics, card, mouseX, mouseY);
        if (waiting) {
            graphics.drawString(font, tr("screen.lobotocraft.player_department.confirming"),
                    panelX + 16, panelY + panelH - 27,
                    LobotomyUiTheme.TEXT_MUTED, false);
        } else {
            EmployeeDepartment selected = EmployeeDepartment.byId(selectedId);
            String selectedText = selected == EmployeeDepartment.UNASSIGNED
                    ? tr("screen.lobotocraft.player_department.not_selected")
                    : tr("screen.lobotocraft.player_department.pending", departmentName(selected));
            graphics.drawString(font, selectedText, panelX + 16, panelY + panelH - 27,
                    selected == EmployeeDepartment.UNASSIGNED
                            ? LobotomyUiTheme.TEXT_MUTED
                            : 0xFF000000 | selected.textColorRgb(), false);
        }
        updateButtonState();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, Card card, int mouseX, int mouseY) {
        CompoundTag data = card.data;
        int color = data.getInt("Color");
        boolean open = data.getBoolean("Open");
        boolean selected = data.getString("Id").equals(selectedId);
        boolean current = data.getBoolean("Selected");
        boolean hovered = inside(mouseX, mouseY, card.x, card.y, card.w, card.h);

        graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h,
                open ? LobotomyUiTheme.BG_ROW_ODD : 0xFF09090D);
        if (open) graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h,
                LobotomyUiRenderer.withAlpha(color, selected ? 0.20F : hovered ? 0.10F : 0.045F));
        drawOutline(graphics, card.x, card.y, card.w, card.h,
                open ? selected ? color : hovered ? LobotomyUiTheme.LINE_BRIGHT
                        : LobotomyUiRenderer.withAlpha(color, 0.72F)
                        : LobotomyUiTheme.LINE_FAINT);
        graphics.fill(card.x, card.y, card.x + card.w, card.y + 3,
                open ? color : LobotomyUiTheme.LINE_FAINT);

        String id = data.getString("Id");
        ResourceLocation icon = ResourceUtil.createInstance(
                "textures/gui/theme/dept_" + id + ".png");
        boolean compact = card.h < 90;
        int iconSize = compact ? Math.min(18, Math.max(12, card.w / 4))
                : Math.min(30, Math.max(20, card.w / 4));
        int iconX = card.x + (card.w - iconSize) / 2;
        int iconY = card.y + (compact ? 7 : 17);
        LobotomyUiRenderer.blitScaled(graphics, icon, iconX, iconY,
                iconSize, iconSize, 16, 16);
        if (!open) graphics.fill(iconX, iconY, iconX + iconSize,
                iconY + iconSize, 0xAA08080C);

        String name = LobotomyUiRenderer.fitText(font,
                tr("department.lobotocraft." + id), card.w - 10);
        graphics.drawCenteredString(font, name, card.x + card.w / 2,
                card.y + (compact ? 27 : 56),
                open ? color : LobotomyUiTheme.TEXT_DISABLED);
        String layer = layerName(id);
        if (!compact) {
            graphics.drawCenteredString(font, layer, card.x + card.w / 2, card.y + 70,
                    open ? LobotomyUiTheme.TEXT_MUTED : LobotomyUiTheme.TEXT_DISABLED);
        }

        String status;
        int statusColor;
        if (!open) {
            status = tr("screen.lobotocraft.player_department.status.locked");
            statusColor = LobotomyUiTheme.TEXT_DISABLED;
        } else if (current) {
            status = tr("screen.lobotocraft.player_department.status.current");
            statusColor = color;
        } else {
            status = tr("screen.lobotocraft.player_department.status.level",
                    roman(data.getInt("Level")));
            statusColor = LobotomyUiTheme.TEXT_BODY;
        }
        graphics.drawCenteredString(font, status, card.x + card.w / 2,
                card.y + (compact ? card.h - 14 : 94),
                statusColor);
        if (selected) graphics.fill(card.x + 5, card.y + card.h - 5,
                card.x + card.w - 5, card.y + card.h - 3, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || waiting) return false;
        for (Card card : cards) {
            if (card.data.getBoolean("Open")
                    && inside(mouseX, mouseY, card.x, card.y, card.w, card.h)) {
                selectedId = card.data.getString("Id");
                updateButtonState();
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        if (waiting || selectedId == null || selectedId.isBlank()
                || selectedId.equals(snapshot.getString("CurrentDepartment"))) return;
        waiting = true;
        updateButtonState();
        MessageLoader.getLoader().sendToServer(new SetPlayerDepartmentPacket(selectedId));
    }

    private void updateButtonState() {
        if (confirmButton != null) {
            confirmButton.active = !waiting && selectedId != null && !selectedId.isBlank()
                    && !selectedId.equals(snapshot.getString("CurrentDepartment"))
                    && cards.stream().anyMatch(card -> card.data.getBoolean("Open")
                    && selectedId.equals(card.data.getString("Id")));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        if (parent instanceof FacilityManagementScreen) {
            MessageLoader.getLoader().sendToServer(
                    RequestFacilityManagementPacket.returnToFacility());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String layerName(String departmentId) {
        return switch (departmentId) {
            case "control", "information", "training", "safety" -> "ASIYAH";
            case "central", "welfare", "disciplinary" -> "BRIAH";
            default -> "ATZILUTH";
        };
    }

    private static String departmentName(EmployeeDepartment department) {
        return tr("department.lobotocraft." + department.id());
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "-";
        };
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y,
                                    int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private record Card(CompoundTag data, int x, int y, int w, int h) {
    }
}
