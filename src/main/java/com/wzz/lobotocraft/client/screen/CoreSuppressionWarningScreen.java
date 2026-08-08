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
        super(Component.literal("核心抑制视觉安全警告"));
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

        String warningTitle = type == CoreSuppressionType.YESOD
                ? "高强度像素化、光敏性癫痫与光影兼容警告"
                : "光敏性癫痫与光影兼容警告";
        graphics.drawCenteredString(font, warningTitle,
                width / 2, panelY + 16, 0xFFFF5353);
        graphics.drawCenteredString(font, type.getDisplayName() + " 核心抑制",
                width / 2, panelY + 34, 0xFF000000 | type.getColor());

        int textY = panelY + 57;
        if (type == CoreSuppressionType.YESOD) {
            textY = drawWrapped(graphics,
                    "Yesod 完整效果会随阶段持续降低画面分辨率，并加入彩色噪点与故障闪烁，后期可能严重妨碍辨认画面。",
                    panelX + 18, textY, panelWidth - 36, 0xFFE6E6E6);
            textY = drawWrapped(graphics,
                    "安全模式仍保留与当前阶段相同的像素化，只关闭彩色噪点和故障闪烁。两种模式均可能与光影包不兼容。",
                    panelX + 18, textY + 5, panelWidth - 36, 0xFFFFC46B);
        } else {
            textY = drawWrapped(graphics,
                    "完整视觉效果包含闪烁、彩色噪点、扫描线、色差与画面扭曲，可能引起不适，并可能诱发光敏性癫痫发作。",
                    panelX + 18, textY, panelWidth - 36, 0xFFE6E6E6);
            textY = drawWrapped(graphics,
                    "完整效果可能与光影包不兼容。仅颜色滤镜只使用一次简单后处理，兼容性更高，但仍无法保证兼容所有光影。",
                    panelX + 18, textY + 5, panelWidth - 36, 0xFFBDBDBD);
        }
        textY = drawWrapped(graphics,
                "如无法继续，请输入 " + STOP_COMMAND + " 停止抑制（不视为完成）。",
                panelX + 18, textY + 5, panelWidth - 36, 0xFFFF7A7A);

        String status = "等待确认：" + CoreSuppressionWarningClientState.getConfirmed()
                + "/" + CoreSuppressionWarningClientState.getTotal()
                + "    剩余 " + CoreSuppressionWarningClientState.getRemainingSeconds() + " 秒";
        graphics.drawCenteredString(font, status, width / 2, textY + 11, 0xFFFFD76A);

        int buttonWidth = (panelWidth - 48) / 2;
        int buttonY = panelBottom - BUTTON_HEIGHT - 18;
        int fullX = panelX + 16;
        int colorX = panelX + panelWidth - 16 - buttonWidth;
        renderChoice(graphics, Choice.FULL, fullX, buttonY, buttonWidth, BUTTON_HEIGHT,
                mouseX, mouseY, "长按开启完整效果", 0xFFFF3838);
        renderChoice(graphics, Choice.COLOR_ONLY, colorX, buttonY, buttonWidth, BUTTON_HEIGHT,
                mouseX, mouseY,
                type == CoreSuppressionType.YESOD ? "长按安全模式" : "长按仅保留颜色",
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
        String hint = holding == choice ? Math.round(progress * 100.0F) + "%" : "持续按住 2 秒";
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Choice { FULL, COLOR_ONLY }
}
