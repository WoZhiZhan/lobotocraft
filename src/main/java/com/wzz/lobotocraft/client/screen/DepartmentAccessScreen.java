package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessData;
import com.wzz.lobotocraft.llm.LlmDepartmentAccessService;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestLlmConfigPacket;
import com.wzz.lobotocraft.network.packet.UpdateDepartmentAccessPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DepartmentAccessScreen extends Screen {
    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 430;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 42;
    private static final int PLAYER_ROW_H = 25;
    private static final int DEPARTMENT_CELL_H = 36;
    private static final EmployeeDepartment[] DEPARTMENTS =
            LlmDepartmentAccessData.allDepartments().toArray(EmployeeDepartment[]::new);

    private final List<PlayerEntry> players = new ArrayList<>();
    private final Map<UUID, EnumSet<EmployeeDepartment>> drafts = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();
    private final EnumSet<EmployeeDepartment> openDepartments =
            EnumSet.noneOf(EmployeeDepartment.class);
    private UUID selectedPlayer;
    private int playerScroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int detailX;
    private int detailY;
    private int detailWidth;
    private LobotomyButton saveButton;
    private boolean waitingForServer;

    public DepartmentAccessScreen(CompoundTag snapshot) {
        super(Component.literal("LLM 部门调度权限"));
        readSnapshot(snapshot);
    }

    public void updateSnapshot(CompoundTag snapshot) {
        UUID keep = selectedPlayer;
        openDepartments.clear();
        players.clear();
        drafts.clear();
        dirtyPlayers.clear();
        readSnapshot(snapshot);
        if (keep != null && players.stream().anyMatch(player -> player.id().equals(keep))) {
            selectedPlayer = keep;
        }
        waitingForServer = false;
        playerScroll = Mth.clamp(playerScroll, 0, maxPlayerScroll());
        updateButtonState();
    }

    private void readSnapshot(CompoundTag snapshot) {
        ListTag openTags = snapshot.getList("OpenDepartments", Tag.TAG_STRING);
        for (int index = 0; index < openTags.size(); index++) {
            EmployeeDepartment department = EmployeeDepartment.byId(openTags.getString(index));
            if (department != EmployeeDepartment.UNASSIGNED) openDepartments.add(department);
        }
        ListTag rows = snapshot.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            if (!row.hasUUID("Id")) continue;
            EnumSet<EmployeeDepartment> departments = EnumSet.noneOf(EmployeeDepartment.class);
            ListTag departmentTags = row.getList("Departments", Tag.TAG_STRING);
            for (int departmentIndex = 0; departmentIndex < departmentTags.size(); departmentIndex++) {
                EmployeeDepartment department = EmployeeDepartment.byId(
                        departmentTags.getString(departmentIndex));
                if (department != EmployeeDepartment.UNASSIGNED) departments.add(department);
            }
            PlayerEntry entry = new PlayerEntry(row.getUUID("Id"), row.getString("Name"),
                    row.getBoolean("Online"), row.getBoolean("Operator"),
                    row.getBoolean("Explicit"));
            players.add(entry);
            drafts.put(entry.id(), departments);
        }
        players.sort(Comparator.comparing(PlayerEntry::online).reversed()
                .thenComparing(PlayerEntry::name, String.CASE_INSENSITIVE_ORDER));
        if (!players.isEmpty()) selectedPlayer = players.get(0).id();
    }

    @Override
    public void init() {
        panelWidth = Math.max(1, Math.min(MAX_WIDTH, width - 8));
        panelHeight = Math.max(1, Math.min(MAX_HEIGHT, height - 8));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listX = panelX + 12;
        listY = panelY + HEADER_H + 25;
        listWidth = Math.min(220, panelWidth / 3);
        listHeight = panelHeight - HEADER_H - FOOTER_H - 31;
        detailX = listX + listWidth + 14;
        detailY = panelY + HEADER_H + 10;
        detailWidth = panelX + panelWidth - 12 - detailX;

        int footerY = panelY + panelHeight - 30;
        boolean compactFooter = panelWidth < 620;
        int selectX = compactFooter ? panelX + 12 : detailX;
        int clearX = selectX + 66;
        int saveX = panelX + panelWidth - (compactFooter ? 204 : 218);
        int backX = panelX + panelWidth - (compactFooter ? 106 : 112);
        addRenderableWidget(LobotomyButton.create(Component.literal("全选"), button -> selectAll())
                .bounds(selectX, footerY, 60, 20).build());
        addRenderableWidget(LobotomyButton.create(Component.literal("清空"), button -> clearSelection())
                .bounds(clearX, footerY, 60, 20).build());
        saveButton = addRenderableWidget(LobotomyButton.create(Component.literal("保存更改"), button -> save())
                .bounds(saveX, footerY, compactFooter ? 92 : 96, 20).build());
        addRenderableWidget(LobotomyButton.create(Component.literal("返回配置"), button -> returnToConfig())
                .bounds(backX, footerY, compactFooter ? 94 : 100, 20).build());
        updateButtonState();
    }

    private void selectAll() {
        EnumSet<EmployeeDepartment> selected = selectedDepartments();
        if (selected == null) return;
        selected.clear();
        selected.addAll(openDepartments);
        markDirty();
    }

    private void clearSelection() {
        EnumSet<EmployeeDepartment> selected = selectedDepartments();
        if (selected == null) return;
        selected.clear();
        markDirty();
    }

    private void save() {
        if (waitingForServer || dirtyPlayers.isEmpty()) return;
        ArrayList<LlmDepartmentAccessService.Update> updates = new ArrayList<>(dirtyPlayers.size());
        for (UUID playerId : dirtyPlayers) {
            updates.add(new LlmDepartmentAccessService.Update(playerId, drafts.get(playerId)));
        }
        waitingForServer = true;
        updateButtonState();
        MessageLoader.getLoader().sendToServer(new UpdateDepartmentAccessPacket(updates));
    }

    private void returnToConfig() {
        MessageLoader.getLoader().sendToServer(new RequestLlmConfigPacket());
    }

    private void markDirty() {
        if (selectedPlayer == null) return;
        dirtyPlayers.add(selectedPlayer);
        updateButtonState();
        playClick();
    }

    private void updateButtonState() {
        if (saveButton != null) saveButton.active = !waitingForServer && !dirtyPlayers.isEmpty();
    }

    private EnumSet<EmployeeDepartment> selectedDepartments() {
        return selectedPlayer == null ? null : drafts.get(selectedPlayer);
    }

    private PlayerEntry selectedEntry() {
        if (selectedPlayer == null) return null;
        for (PlayerEntry player : players) if (player.id().equals(selectedPlayer)) return player;
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "DEPARTMENT DISPATCH ACCESS", "LLM 调度权限");
        renderPlayerList(graphics, mouseX, mouseY);
        renderDepartmentGrid(graphics, mouseX, mouseY);
        if (!dirtyPlayers.isEmpty()) {
            String text = "待保存 " + dirtyPlayers.size();
            LobotomyUiRenderer.drawTag(graphics, font, detailX + 146,
                    panelY + panelHeight - 29, text, LobotomyUiTheme.GOLD);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "玩家", listX, panelY + HEADER_H + 9,
                LobotomyUiTheme.TEXT_TITLE, false);
        LobotomyUiRenderer.drawInsetBox(graphics, listX, listY, listWidth, listHeight);
        graphics.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        int y = listY + 3 - playerScroll * PLAYER_ROW_H;
        for (PlayerEntry player : players) {
            boolean selected = player.id().equals(selectedPlayer);
            boolean hovered = inside(mouseX, mouseY, listX + 3, y, listWidth - 6, PLAYER_ROW_H - 2);
            int rowColor = selected ? 0xA02A514E : hovered ? 0x70303B39 : 0x50101312;
            graphics.fill(listX + 3, y, listX + listWidth - 3, y + PLAYER_ROW_H - 2, rowColor);
            drawOutline(graphics, listX + 3, y, listWidth - 6, PLAYER_ROW_H - 2,
                    selected ? LobotomyUiTheme.LINE_ACCENT : LobotomyUiTheme.LINE_FAINT);
            int dotColor = player.online() ? LobotomyUiTheme.ACCENT : LobotomyUiTheme.TEXT_MUTED;
            graphics.fill(listX + 8, y + 9, listX + 12, y + 13, dotColor);
            String name = LobotomyUiRenderer.fitText(font, player.name(), listWidth - 68);
            graphics.drawString(font, name, listX + 17, y + 7,
                    player.online() ? LobotomyUiTheme.TEXT_TITLE : LobotomyUiTheme.TEXT_MUTED, false);
            String badge = player.operator() ? "OP" : player.explicit() ? "SET" : "-";
            graphics.drawString(font, badge, listX + listWidth - 8 - font.width(badge), y + 7,
                    player.operator() ? LobotomyUiTheme.GOLD : LobotomyUiTheme.TEXT_MUTED, false);
            y += PLAYER_ROW_H;
        }
        graphics.disableScissor();

        int maxScroll = maxPlayerScroll();
        if (maxScroll > 0) {
            int trackY = listY + 3;
            int trackH = listHeight - 6;
            int thumbH = Math.max(18, trackH * visiblePlayerRows() / players.size());
            int thumbY = trackY + (trackH - thumbH) * playerScroll / maxScroll;
            graphics.fill(listX + listWidth - 3, trackY,
                    listX + listWidth - 1, trackY + trackH, LobotomyUiTheme.LINE_FAINT);
            graphics.fill(listX + listWidth - 3, thumbY,
                    listX + listWidth - 1, thumbY + thumbH, LobotomyUiTheme.LINE_ACCENT);
        }
    }

    private void renderDepartmentGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerEntry selected = selectedEntry();
        if (selected == null) {
            graphics.drawCenteredString(font, "无可配置玩家",
                    detailX + detailWidth / 2, detailY + 50, LobotomyUiTheme.TEXT_MUTED);
            return;
        }
        graphics.drawString(font, selected.name(), detailX, detailY,
                LobotomyUiTheme.TEXT_TITLE, false);
        String status = selected.online() ? "在线" : "离线配置";
        if (!selected.explicit() && selected.operator()) status += " · 默认全部门";
        graphics.drawString(font, status, detailX, detailY + 14,
                LobotomyUiTheme.TEXT_MUTED, false);

        EnumSet<EmployeeDepartment> selectedDepartments = selectedDepartments();
        int selectedCount = selectedDepartments == null ? 0 : selectedDepartments.size();
        String count = selectedCount + "/" + DEPARTMENTS.length;
        LobotomyUiRenderer.drawTag(graphics, font,
                detailX + detailWidth - font.width(count) - 8, detailY,
                count, selectedCount == 0 ? LobotomyUiTheme.ALERT_RED : LobotomyUiTheme.ACCENT);

        int gridY = detailY + 39;
        int gap = 8;
        int cellWidth = (detailWidth - gap) / 2;
        boolean compactGrid = panelHeight < 330;
        int cellHeight = compactGrid ? 22 : DEPARTMENT_CELL_H;
        int rowGap = compactGrid ? 4 : 7;
        for (int index = 0; index < DEPARTMENTS.length; index++) {
            EmployeeDepartment department = DEPARTMENTS[index];
            int column = index % 2;
            int row = index / 2;
            int x = detailX + column * (cellWidth + gap);
            int y = gridY + row * (cellHeight + rowGap);
            boolean checked = selectedDepartments != null && selectedDepartments.contains(department);
            boolean departmentOpen = openDepartments.contains(department);
            boolean hovered = inside(mouseX, mouseY, x, y, cellWidth, cellHeight);
            int color = LobotomyUiTheme.departmentColor(department.id());
            graphics.fill(x, y, x + cellWidth, y + cellHeight,
                    !departmentOpen ? 0xE0050605
                            : checked ? 0xA016211E : hovered ? 0x70151B19 : 0xD0080A09);
            drawOutline(graphics, x, y, cellWidth, cellHeight,
                    !departmentOpen ? LobotomyUiTheme.LINE_FAINT
                            : checked || hovered ? color : LobotomyUiTheme.LINE_FAINT);
            int checkX = x + (compactGrid ? 5 : 7);
            int checkY = y + (compactGrid ? 5 : 9);
            int checkSize = compactGrid ? 12 : 14;
            graphics.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, 0xFF070908);
            drawOutline(graphics, checkX, checkY, checkSize, checkSize, checked ? color : LobotomyUiTheme.LINE_MID);
            if (checked) {
                graphics.fill(checkX + 3, checkY + 3, checkX + checkSize - 3, checkY + checkSize - 3, color);
            }
            graphics.drawString(font, department.displayName(), x + (compactGrid ? 22 : 29),
                    y + (compactGrid ? 7 : 8),
                    !departmentOpen ? LobotomyUiTheme.TEXT_MUTED
                            : checked ? color : LobotomyUiTheme.TEXT_TITLE, false);
            if (!compactGrid) {
                graphics.drawString(font, departmentOpen
                                ? department.id().toUpperCase() : "尚未开放", x + 29, y + 20,
                        LobotomyUiTheme.TEXT_MUTED, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (waitingForServer) return true;
        int y = listY + 3 - playerScroll * PLAYER_ROW_H;
        for (PlayerEntry player : players) {
            if (inside(mouseX, mouseY, listX + 3, y, listWidth - 6, PLAYER_ROW_H - 2)) {
                selectedPlayer = player.id();
                playClick();
                return true;
            }
            y += PLAYER_ROW_H;
        }
        int gridY = detailY + 39;
        int gap = 8;
        int cellWidth = (detailWidth - gap) / 2;
        int cellHeight = panelHeight < 330 ? 22 : DEPARTMENT_CELL_H;
        int rowGap = panelHeight < 330 ? 4 : 7;
        EnumSet<EmployeeDepartment> selected = selectedDepartments();
        if (selected == null) return false;
        for (int index = 0; index < DEPARTMENTS.length; index++) {
            int x = detailX + (index % 2) * (cellWidth + gap);
            int cellY = gridY + (index / 2) * (cellHeight + rowGap);
            if (!inside(mouseX, mouseY, x, cellY, cellWidth, cellHeight)) continue;
            EmployeeDepartment department = DEPARTMENTS[index];
            if (!openDepartments.contains(department)) {
                if (minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.VILLAGER_NO, 0.3F, 0.8F);
                }
                return true;
            }
            if (!selected.remove(department)) selected.add(department);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            playerScroll = Mth.clamp(playerScroll - (int) Math.signum(delta), 0, maxPlayerScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int visiblePlayerRows() {
        return Math.max(1, (listHeight - 6) / PLAYER_ROW_H);
    }

    private int maxPlayerScroll() {
        return Math.max(0, players.size() - visiblePlayerRows());
    }

    private void playClick() {
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.15F);
        }
    }

    @Override
    public void onClose() {
        returnToConfig();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y,
                                    int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record PlayerEntry(UUID id, String name, boolean online,
                               boolean operator, boolean explicit) {
    }
}
