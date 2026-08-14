package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.core.CoreSuppressionWarningClientState;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CoreSuppressionVisualChoicePacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public final class CoreSuppressionWarningScreen extends Screen {
    private static final int HOLD_TICKS = 40;
    private static final int PANEL_WIDTH = 430;
    private static final int BUTTON_HEIGHT = 38;
    private static final String STOP_COMMAND = "/lobotocraft debug suppression end false";

    private final CoreSuppressionType type;
    private Choice holding;
    private int heldTicks;
    private boolean submitted;

    public CoreSuppressionWarningScreen(CoreSuppressionType type) {
        super(Component.translatable("screen.lobotocraft.core_suppression_warning.title"));
        this.type = type;
    }

    @Override
    public void tick() {
        super.tick();
        if (submitted || holding == null) return;
        long window = minecraft.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            resetHold();
            return;
        }
        heldTicks = Math.min(HOLD_TICKS, heldTicks + 1);
        if (heldTicks >= HOLD_TICKS) submit(holding == Choice.FULL);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = Math.min(PANEL_WIDTH, width - 28);
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(18, (height - 270) / 2);
        int panelBottom = Math.min(height - 18, panelY + 270);
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelBottom + 2, 0xFF9F2020);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelBottom, 0xF20A0A0A);

        String warningTitle = tr(type == CoreSuppressionType.YESOD
                ? "screen.lobotocraft.core_suppression_warning.yesod_heading"
                : "screen.lobotocraft.core_suppression_warning.heading");
        graphics.drawCenteredString(font, warningTitle,
                width / 2, panelY + 16, 0xFFFF5353);
        graphics.drawCenteredString(font,
                tr("screen.lobotocraft.core_suppression_warning.suppression",
                        tr("core_suppression.lobotocraft." + type.getId())),
                width / 2, panelY + 34, 0xFF000000 | type.getColor());

        int textY = panelY + 57;
        if (type == CoreSuppressionType.YESOD) {
            textY = drawWrapped(graphics,
                    tr("screen.lobotocraft.core_suppression_warning.yesod.full"),
                    panelX + 18, textY, panelWidth - 36, 0xFFE6E6E6);
            textY = drawWrapped(graphics,
                    tr("screen.lobotocraft.core_suppression_warning.yesod.safe"),
                    panelX + 18, textY + 5, panelWidth - 36, 0xFFFFC46B);
        } else {
            textY = drawWrapped(graphics,
                    tr("screen.lobotocraft.core_suppression_warning.full"),
                    panelX + 18, textY, panelWidth - 36, 0xFFE6E6E6);
            textY = drawWrapped(graphics,
                    tr("screen.lobotocraft.core_suppression_warning.color_only"),
                    panelX + 18, textY + 5, panelWidth - 36, 0xFFBDBDBD);
        }
        textY = drawWrapped(graphics,
                tr("screen.lobotocraft.core_suppression_warning.stop", STOP_COMMAND),
                panelX + 18, textY + 5, panelWidth - 36, 0xFFFF7A7A);

        String status = tr("screen.lobotocraft.core_suppression_warning.status",
                CoreSuppressionWarningClientState.getConfirmed(),
                CoreSuppressionWarningClientState.getTotal(),
                CoreSuppressionWarningClientState.getRemainingSeconds());
        graphics.drawCenteredString(font, status, width / 2, textY + 11, 0xFFFFD76A);

        int buttonWidth = (panelWidth - 48) / 2;
        int buttonY = panelBottom - BUTTON_HEIGHT - 18;
        int fullX = panelX + 16;
        int colorX = panelX + panelWidth - 16 - buttonWidth;
        renderChoice(graphics, Choice.FULL, fullX, buttonY, buttonWidth, BUTTON_HEIGHT,
                mouseX, mouseY,
                tr("screen.lobotocraft.core_suppression_warning.choice.full"), 0xFFFF3838);
        renderChoice(graphics, Choice.COLOR_ONLY, colorX, buttonY, buttonWidth, BUTTON_HEIGHT,
                mouseX, mouseY,
                tr(type == CoreSuppressionType.YESOD
                        ? "screen.lobotocraft.core_suppression_warning.choice.safe"
                        : "screen.lobotocraft.core_suppression_warning.choice.color_only"),
                0xFF8CD9A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderChoice(GuiGraphics graphics, Choice choice, int x, int y, int w, int h,
                              int mouseX, int mouseY, String label, int color) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF191919 : 0xFF101010);
        outline(graphics, x, y, w, h, hovered ? 0xFFFFFFFF : color);
        float progress = holding == choice ? heldTicks / (float) HOLD_TICKS : 0.0F;
        if (progress > 0.0F) {
            graphics.fill(x + 2, y + h - 5, x + 2 + Math.round((w - 4) * progress), y + h - 2, color);
        }
        graphics.drawCenteredString(font, label, x + w / 2, y + 10, color);
        String hint = holding == choice ? Math.round(progress * 100.0F) + "%"
                : tr("screen.lobotocraft.core_suppression_warning.hold");
        graphics.drawCenteredString(font, hint, x + w / 2, y + 23, 0xFF999999);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || submitted) return true;
        int panelWidth = Math.min(PANEL_WIDTH, width - 28);
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(18, (height - 270) / 2);
        int panelBottom = Math.min(height - 18, panelY + 270);
        int buttonWidth = (panelWidth - 48) / 2;
        int buttonY = panelBottom - BUTTON_HEIGHT - 18;
        int fullX = panelX + 16;
        int colorX = panelX + panelWidth - 16 - buttonWidth;
        if (inside(mouseX, mouseY, fullX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            holding = Choice.FULL;
            heldTicks = 0;
        } else if (inside(mouseX, mouseY, colorX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            holding = Choice.COLOR_ONLY;
            heldTicks = 0;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!submitted) resetHold();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit(boolean fullEffects) {
        if (submitted) return;
        submitted = true;
        MessageLoader.getLoader().sendToServer(new CoreSuppressionVisualChoicePacket(fullEffects));
        CoreSuppressionWarningClientState.choiceSubmitted();
    }

    private void resetHold() {
        holding = null;
        heldTicks = 0;
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Choice { FULL, COLOR_ONLY }
}
