package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.SetElevatorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ElevatorScreen extends Screen {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private EditBox distanceInput;
    private Button upButton;
    private Button downButton;
    private int leftPos;
    private int topPos;

    // 当前选择的方向：true=上，false=下
    private boolean isUp;
    // 来自BlockEntity的初始值
    private final int defaultDistance;
    private final boolean defaultIsUp;
    // 方块坐标
    private final BlockPos blockPos;

    public ElevatorScreen(BlockPos blockPos, int savedDistance, boolean savedIsUp) {
        super(Component.translatable("gui.lobotocraft.elevator.title"));
        this.blockPos = blockPos;
        this.defaultDistance = savedDistance;
        this.defaultIsUp = savedIsUp;
        this.isUp = savedIsUp;
    }

    @Override
    public void init() {
        super.init();

        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        // 距离输入框
        this.distanceInput = new EditBox(
                this.font,
                this.leftPos + 38,
                this.topPos + 45,
                100,
                20,
                Component.translatable("gui.lobotocraft.elevator.distance")
        );
        this.distanceInput.setValue(String.valueOf(defaultDistance));
        this.distanceInput.setMaxLength(4);
        this.distanceInput.setFilter(s -> s.matches("\\d*"));
        this.addWidget(this.distanceInput);

        // 向上按钮（切换选择）
        this.upButton = Button.builder(
                        Component.translatable("gui.lobotocraft.elevator.up"),
                        button -> {
                            this.isUp = true;
                            refreshButtonStyles();
                        }
                )
                .bounds(this.leftPos + 20, this.topPos + 85, 60, 40)
                .build();
        this.addRenderableWidget(this.upButton);

        // 向下按钮（切换选择）
        this.downButton = Button.builder(
                        Component.translatable("gui.lobotocraft.elevator.down"),
                        button -> {
                            this.isUp = false;
                            refreshButtonStyles();
                        }
                )
                .bounds(this.leftPos + 96, this.topPos + 85, 60, 40)
                .build();
        this.addRenderableWidget(this.downButton);

        // 确认按钮
        Button confirmButton = Button.builder(
                        Component.translatable("gui.lobotocraft.elevator.confirm"),
                        button -> onConfirm()
                )
                .bounds(this.leftPos + 38, this.topPos + 138, 100, 20)
                .build();
        this.addRenderableWidget(confirmButton);

        refreshButtonStyles();
    }

    private void refreshButtonStyles() {
        this.upButton.active = true;
        this.downButton.active = true;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        renderGuiBackground(guiGraphics);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title,
                this.leftPos + GUI_WIDTH / 2, this.topPos + 8, 0xFFFFFF);

        // 分隔线（标题下方）
        guiGraphics.fill(this.leftPos + 10, this.topPos + 22,
                this.leftPos + GUI_WIDTH - 10, this.topPos + 23, 0xFF4A90E2);

        // "传送距离" 标签
        guiGraphics.drawString(this.font,
                Component.translatable("gui.lobotocraft.elevator.distance_label"),
                this.leftPos + 38, this.topPos + 32, 0xFFFFFF);

        // 单位标签
        guiGraphics.drawString(this.font,
                Component.translatable("gui.lobotocraft.elevator.blocks"),
                this.leftPos + 143, this.topPos + 50, 0xAAAAAA);

        // "传送方向" 标签
        guiGraphics.drawString(this.font,
                Component.translatable("gui.lobotocraft.elevator.direction_label"),
                this.leftPos + 38, this.topPos + 72, 0xFFFFFF);

        // 输入框
        this.distanceInput.render(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染按钮（super会渲染所有RenderableWidget）
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 在按钮上层渲染箭头和选中高亮
        renderDirectionButtons(guiGraphics);
    }

    private void renderGuiBackground(GuiGraphics guiGraphics) {
        // 主面板
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0xC0101010);

        // 顶部条
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + GUI_WIDTH, this.topPos + 22, 0xFF2A5A8A);

        // 边框
        renderBorder(guiGraphics, this.leftPos, this.topPos, GUI_WIDTH, GUI_HEIGHT, 0xFF4A90E2);
    }

    private void renderDirectionButtons(GuiGraphics guiGraphics) {
        int upX = this.leftPos + 20;
        int upY = this.topPos + 85;
        int downX = this.leftPos + 96;
        int downY = this.topPos + 85;
        int btnW = 60;
        int btnH = 40;

        // 选中的按钮画一个高亮边框
        if (this.isUp) {
            renderBorder(guiGraphics, upX, upY, btnW, btnH, 0xFF4A90E2);
        } else {
            renderBorder(guiGraphics, downX, downY, btnW, btnH, 0xFF4A90E2);
        }

        // 画箭头图标（在按钮文字上方）
        drawArrowUp(guiGraphics, upX + btnW / 2, upY + 12, this.isUp ? 0xFFFFFF : 0xFF888888);
        drawArrowDown(guiGraphics, downX + btnW / 2, downY + 12, !this.isUp ? 0xFFFFFF : 0xFF888888);
    }

    private void renderBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 2, color);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 2, y + height, color);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, color);
    }

    private void drawArrowUp(GuiGraphics guiGraphics, int x, int y, int color) {
        // 三角头部
        for (int i = 0; i < 6; i++) {
            guiGraphics.fill(x - i, y - 6 + i, x + i + 1, y - 4 + i, color);
        }
        // 竖杆
        guiGraphics.fill(x - 2, y, x + 3, y + 6, color);
    }

    private void drawArrowDown(GuiGraphics guiGraphics, int x, int y, int color) {
        // 竖杆
        guiGraphics.fill(x - 2, y - 6, x + 3, y, color);
        // 三角头部
        for (int i = 0; i < 6; i++) {
            guiGraphics.fill(x - i, y + 4 - i, x + i + 1, y + 6 - i, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.distanceInput.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.distanceInput);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.distanceInput.isFocused()) {
            return this.distanceInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.distanceInput.isFocused()) {
            return this.distanceInput.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private int getDistance() {
        try {
            String value = this.distanceInput.getValue();
            if (value.isEmpty()) return 10;
            int distance = Integer.parseInt(value);
            return Math.max(1, Math.min(distance, 1000));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    /**
     * 点击确认 → 发送网络包到服务端保存设置
     */
    private void onConfirm() {
        int distance = getDistance();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(
                    "已设置电梯的传送方向为：" + (this.isUp ? "上" : "下") + " 距离：" + distance
            ));
        }
        MessageLoader.getLoader().sendToServer(new SetElevatorPacket(this.blockPos, distance, this.isUp));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}