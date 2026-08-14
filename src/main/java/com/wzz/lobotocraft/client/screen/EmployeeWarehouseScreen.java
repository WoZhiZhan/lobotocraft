package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.menu.EmployeeWarehouseMenu;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class EmployeeWarehouseScreen extends AbstractContainerScreen<EmployeeWarehouseMenu> {
    private static final int SLOT_BORDER = 0xFF46464F;
    private LobotomyButton previous;
    private LobotomyButton next;

    public EmployeeWarehouseScreen(EmployeeWarehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 233;
        inventoryLabelY = EmployeeWarehouseMenu.PLAYER_LABEL_Y;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(LobotomyButton.create(Component.literal("↓"),
                        button -> press(EmployeeWarehouseMenu.BUTTON_COLLECT_ALL))
                .bounds(leftPos + 68, topPos + 3, 17, 17)
                .tooltip(Tooltip.create(Component.translatable(
                        "screen.lobotocraft.employee_warehouse.collect_all"))).build());
        previous = addRenderableWidget(LobotomyButton.create(Component.literal("<"),
                        button -> press(EmployeeWarehouseMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 89, topPos + 3, 18, 17)
                .tooltip(Tooltip.create(Component.translatable(
                        "screen.lobotocraft.common.previous_page"))).build());
        next = addRenderableWidget(LobotomyButton.create(Component.literal(">"),
                        button -> press(EmployeeWarehouseMenu.BUTTON_NEXT))
                .bounds(leftPos + 151, topPos + 3, 18, 17)
                .tooltip(Tooltip.create(Component.translatable(
                        "screen.lobotocraft.common.next_page"))).build());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (previous != null) previous.active = menu.page() > 0;
        if (next != null) next.active = menu.page() + 1 < menu.pageCount();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, (menu.page() + 1) + " / " + menu.pageCount(),
                leftPos + 128, topPos + 8, 0xFFE6ECE8);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight,
                LobotomyUiTheme.BG_PANEL);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 21,
                LobotomyUiTheme.BG_INSET);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight,
                LobotomyUiTheme.ACCENT);
        drawBorder(graphics, leftPos, topPos, imageWidth, imageHeight,
                LobotomyUiTheme.LINE_MID);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BORDER);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, LobotomyUiTheme.BG_INSET);
        }
        int inventoryLine = topPos + EmployeeWarehouseMenu.PLAYER_LABEL_Y - 5;
        graphics.fill(leftPos + 8, inventoryLine, leftPos + imageWidth - 8, inventoryLine + 1,
                LobotomyUiTheme.LINE_FAINT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LobotomyUiTheme.TEXT_TITLE, false);
        graphics.drawString(font, Component.translatable("screen.lobotocraft.inventory.yours"),
                8, inventoryLabelY,
                LobotomyUiTheme.TEXT_MUTED, false);
    }

    @Override
    public void onClose() {
        super.onClose();
        MessageLoader.getLoader().sendToServer(RequestFacilityManagementPacket.returnToFacility());
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
