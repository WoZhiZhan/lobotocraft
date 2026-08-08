package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.menu.EmployeeInventoryMenu;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class EmployeeInventoryScreen extends AbstractContainerScreen<EmployeeInventoryMenu> {
    private static final int PANEL = 0xF0121518;
    private static final int HEADER = 0xFF20272A;
    private static final int BORDER = 0xFF66736C;
    private static final int SLOT_BORDER = 0xFF46504B;
    private static final int SLOT_INNER = 0xFF171C1E;
    private static final int ACCENT = 0xFF78B58E;
    private static final int TEXT = 0xFFE6ECE8;
    private static final int MUTED = 0xFFAAB5AF;

    public EmployeeInventoryScreen(EmployeeInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.imageWidth();
        imageHeight = menu.imageHeight();
        inventoryLabelY = menu.playerLabelY();
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.getItem().isEmpty()) {
            String identifier = menu.curioIdentifier(hoveredSlot.index);
            if (!identifier.isEmpty()) {
                graphics.renderTooltip(font,
                        Component.translatable("curios.identifier." + identifier), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
        graphics.fill(left, top, left + imageWidth, top + 17, HEADER);
        graphics.fill(left, top, left + 3, top + imageHeight, ACCENT);
        drawBorder(graphics, left, top, imageWidth, imageHeight, BORDER);

        for (Slot slot : menu.slots) {
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BORDER);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
        }

        int dividerY = top + menu.playerLabelY() - 5;
        graphics.fill(left + 8, dividerY, left + imageWidth - 8, dividerY + 1, 0xFF35403B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, menu.employeeName(), titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, Component.literal("饰品槽"), 8,
                EmployeeInventoryMenu.CURIO_LABEL_Y, ACCENT, false);
        if (menu.curioSlotCount() == 0) {
            graphics.drawString(font, Component.literal("无可用饰品槽"), 8,
                    EmployeeInventoryMenu.CURIO_START_Y, MUTED, false);
        }
        graphics.drawString(font, Component.literal("你的物品栏"), 8,
                menu.playerLabelY(), MUTED, false);
    }

    @Override
    public void onClose() {
        super.onClose();
        MessageLoader.getLoader().sendToServer(RequestFacilityManagementPacket.returnToEgoAssignment());
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
