package com.wzz.lobotocraft.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * 脑叶主题按钮：外观全部交给 {@link LobotomyUiRenderer#drawButton}，
 * onPress/Tooltip/active 等行为与原版 Button 完全一致。
 * HOVER/DISABLED 自动判定；额外提供 {@link #setSelected}、{@link #setDanger}。
 */
public class LobotomyButton extends Button {

    private boolean selected;
    private boolean danger;

    protected LobotomyButton(int x, int y, int width, int height,
                             Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    // 不能沿用 builder：父类 Button 有同签名 static builder(...) 但返回 Button.Builder，
    // Java 静态方法隐藏要求返回类型协变，故改名 create 以规避冲突。
    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    /** 选中/已启用态（SELECTED：青白边框 + 底部下划线 + ACCENT 文字）。 */
    public LobotomyButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    /** 危险确认态（DANGER：红边红字）。 */
    public LobotomyButton setDanger(boolean danger) {
        this.danger = danger;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isDanger() {
        return danger;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LobotomyUiRenderer.ButtonState state;
        if (!this.active) {
            state = LobotomyUiRenderer.ButtonState.DISABLED;
        } else if (danger) {
            state = LobotomyUiRenderer.ButtonState.DANGER;
        } else if (selected) {
            state = LobotomyUiRenderer.ButtonState.SELECTED;
        } else if (isHoveredOrFocused()) {
            state = LobotomyUiRenderer.ButtonState.HOVER;
        } else {
            state = LobotomyUiRenderer.ButtonState.NORMAL;
        }
        LobotomyUiRenderer.drawButton(graphics, Minecraft.getInstance().font,
                getX(), getY(), getWidth(), getHeight(), getMessage(), state);
    }

    /** 与 {@link Button.Builder} 同用法的构建器（bounds/pos/size/tooltip/build）。 */
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private Tooltip tooltip;
        private int x;
        private int y;
        private int width = 150;
        private int height = LobotomyUiTheme.BTN_H;
        private boolean selected;
        private boolean danger;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder danger(boolean danger) {
            this.danger = danger;
            return this;
        }

        public LobotomyButton build() {
            LobotomyButton button = new LobotomyButton(x, y, width, height, message, onPress);
            button.setSelected(selected).setDanger(danger);
            if (tooltip != null) button.setTooltip(tooltip);
            return button;
        }
    }
}
