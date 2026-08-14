package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.AcknowledgeWelcomeNoticePacket;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** First-save notice shown once when a player enters a new facility. */
public final class WelcomeNoticeScreen extends Screen {
    private static final int PANEL_WIDTH = 720;
    private static final int PANEL_MARGIN = 18;
    private static final int HEADER_HEIGHT = 62;
    private static final int FOOTER_HEIGHT = 56;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int LINE_GAP = 3;

    private final List<NoticeBlock> blocks = createBlocks();
    private double scrollOffset;
    private int contentHeight;
    private int viewportTop;
    private int viewportBottom;
    private int scrollbarX;
    private boolean draggingScrollbar;
    private double dragOffset;
    private boolean submitted;

    public WelcomeNoticeScreen() {
        super(Component.translatable("screen.lobotocraft.welcome.version"));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelWidth = Math.min(PANEL_WIDTH, width - PANEL_MARGIN * 2);
        int panelHeight = Math.min(760, height - PANEL_MARGIN * 2);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int panelRight = panelX + panelWidth;
        int panelBottom = panelY + panelHeight;
        viewportTop = panelY + HEADER_HEIGHT;
        viewportBottom = panelBottom - FOOTER_HEIGHT;
        scrollbarX = panelRight - 20;

        graphics.fill(panelX - 2, panelY - 2, panelRight + 2, panelBottom + 2, 0xFFB87824);
        graphics.fill(panelX, panelY, panelRight, panelBottom, 0xF2080808);
        graphics.fill(panelX + 1, viewportTop - 1, panelRight - 1, viewportBottom + 1, 0xFF151515);
        graphics.drawCenteredString(font, Component.translatable("screen.lobotocraft.welcome.title"),
                width / 2, panelY + 16, 0xFFFFC46B);
        graphics.drawCenteredString(font, Component.translatable("screen.lobotocraft.welcome.version"),
                width / 2, panelY + 36, 0xFFE6E6E6);

        int contentWidth = Math.max(120, panelWidth - 58);
        List<RenderedLine> lines = buildLines(contentWidth);
        contentHeight = Math.max(1, linesHeight(lines));
        int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop) + 10);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);

        graphics.enableScissor(panelX + 14, viewportTop, scrollbarX - 10, viewportBottom);
        int y = viewportTop + 8 - (int) scrollOffset;
        for (RenderedLine line : lines) {
            if (y + font.lineHeight >= viewportTop && y <= viewportBottom) {
                graphics.drawString(font, line.text(), panelX + 16, y, line.color(), false);
            }
            y += line.height();
        }
        graphics.disableScissor();

        renderScrollbar(graphics, viewportTop, viewportBottom, maxScroll, mouseX, mouseY);
        graphics.fill(panelX, viewportBottom + 2, panelRight, panelBottom, 0xF20A0A0A);
        graphics.drawCenteredString(font, Component.translatable("screen.lobotocraft.welcome.once_notice"), width / 2,
                viewportBottom + 12, 0xFF999999);

        int buttonWidth = Math.min(300, panelWidth - 36);
        int buttonX = width / 2 - buttonWidth / 2;
        int buttonY = panelBottom - 34;
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, 24);
        graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 24,
                hovered ? 0xFF3B2B16 : 0xFF211A11);
        outline(graphics, buttonX, buttonY, buttonWidth, 24, hovered ? 0xFFFFD27A : 0xFFB87824);
        graphics.drawCenteredString(font, Component.translatable("screen.lobotocraft.welcome.confirm"),
                width / 2, buttonY + 8,
                hovered ? 0xFFFFE5B0 : 0xFFFFC46B);
    }

    private void renderScrollbar(GuiGraphics graphics, int top, int bottom, int maxScroll,
                                 int mouseX, int mouseY) {
        int trackHeight = bottom - top;
        int trackX = scrollbarX;
        graphics.fill(trackX, top, trackX + SCROLLBAR_WIDTH, bottom, 0x55444444);
        if (maxScroll <= 0) {
            graphics.fill(trackX, top, trackX + SCROLLBAR_WIDTH, bottom, 0xFFB87824);
            return;
        }
        int handleHeight = Math.max(24, trackHeight * trackHeight / (contentHeight + 10));
        int handleTravel = trackHeight - handleHeight;
        int handleY = top + (int) Math.round(handleTravel * scrollOffset / maxScroll);
        boolean hovered = inside(mouseX, mouseY, trackX - 4, handleY, SCROLLBAR_WIDTH + 8, handleHeight);
        graphics.fill(trackX, handleY, trackX + SCROLLBAR_WIDTH, handleY + handleHeight,
                hovered || draggingScrollbar ? 0xFFFFD27A : 0xFFB87824);
    }

    private List<RenderedLine> buildLines(int maxWidth) {
        List<RenderedLine> lines = new ArrayList<>();
        for (NoticeBlock block : blocks) {
            if (block.heading()) {
                lines.add(new RenderedLine(block.text().getVisualOrderText(), block.color(), font.lineHeight + 8));
            } else {
                List<FormattedCharSequence> wrapped = font.split(block.text(), maxWidth);
                for (FormattedCharSequence line : wrapped) {
                    lines.add(new RenderedLine(line, block.color(), font.lineHeight + LINE_GAP));
                }
                if (block.gap() > 0) {
                    lines.add(new RenderedLine(Component.empty().getVisualOrderText(), 0, block.gap()));
                }
            }
        }
        return lines;
    }

    private static int linesHeight(List<RenderedLine> lines) {
        int result = 0;
        for (RenderedLine line : lines) result += line.height();
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, 0, viewportTop, width, viewportBottom - viewportTop)) {
            int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop) + 10);
            scrollOffset = clamp(scrollOffset - delta * 24.0, 0, maxScroll);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int panelWidth = Math.min(PANEL_WIDTH, width - PANEL_MARGIN * 2);
        int panelHeight = Math.min(760, height - PANEL_MARGIN * 2);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int buttonWidth = Math.min(300, panelWidth - 36);
        int buttonX = width / 2 - buttonWidth / 2;
        int buttonY = panelY + panelHeight - 34;
        if (inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, 24)) {
            confirmAndClose();
            return true;
        }

        int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop) + 10);
        int trackHeight = viewportBottom - viewportTop;
        int handleHeight = maxScroll <= 0 ? trackHeight : Math.max(24, trackHeight * trackHeight / (contentHeight + 10));
        int handleTravel = trackHeight - handleHeight;
        int handleY = viewportTop + (maxScroll <= 0 ? 0 : (int) Math.round(handleTravel * scrollOffset / maxScroll));
        if (inside(mouseX, mouseY, scrollbarX - 4, handleY, SCROLLBAR_WIDTH + 8, handleHeight)) {
            draggingScrollbar = true;
            dragOffset = mouseY - handleY;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingScrollbar || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int trackHeight = viewportBottom - viewportTop;
        int maxScroll = Math.max(0, contentHeight - trackHeight + 10);
        int handleHeight = maxScroll <= 0 ? trackHeight : Math.max(24, trackHeight * trackHeight / (contentHeight + 10));
        int travel = Math.max(1, trackHeight - handleHeight);
        double handleTop = clamp(mouseY - dragOffset, viewportTop, viewportBottom - handleHeight);
        scrollOffset = clamp((handleTop - viewportTop) / travel * maxScroll, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop) + 10);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollOffset = clamp(scrollOffset + (keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? viewportBottom - viewportTop - 20 : 24), 0, maxScroll);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_UP) {
            scrollOffset = clamp(scrollOffset - (keyCode == GLFW.GLFW_KEY_PAGE_UP ? viewportBottom - viewportTop - 20 : 24), 0, maxScroll);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // The notice is intentionally closed only by its acknowledgement button.
    }

    private void confirmAndClose() {
        if (submitted || minecraft == null) return;
        submitted = true;
        MessageLoader.getLoader().sendToServer(new AcknowledgeWelcomeNoticePacket());
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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

    private static List<NoticeBlock> createBlocks() {
        return List.of(
                heading("screen.lobotocraft.welcome.apology.heading", 0xFFFFC46B),
                body("screen.lobotocraft.welcome.apology.1", 0xFFE6E6E6, 4),
                body("screen.lobotocraft.welcome.apology.2", 0xFFE6E6E6, 4),
                body("screen.lobotocraft.welcome.apology.3", 0xFFE6E6E6, 4),
                body("screen.lobotocraft.welcome.apology.4", 0xFFE6E6E6, 8),
                heading("screen.lobotocraft.welcome.announcement.heading", 0xFFFFC46B),
                body("screen.lobotocraft.welcome.announcement.1", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.2", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.3", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.4", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.5", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.6", 0xFFE6E6E6, 2),
                body("screen.lobotocraft.welcome.announcement.7", 0xFFE6E6E6, 6),
                body("screen.lobotocraft.welcome.announcement.8", 0xFFBDBDBD, 8),
                heading("screen.lobotocraft.welcome.statement.heading", 0xFFFFC46B),
                body("screen.lobotocraft.welcome.statement.1", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.statement.2", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.statement.3", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.statement.4", 0xFFE6E6E6, 8),
                heading("screen.lobotocraft.welcome.visual.heading", 0xFFFF6666),
                body("screen.lobotocraft.welcome.visual.1", 0xFFFFB0B0, 4),
                body("screen.lobotocraft.welcome.visual.2", 0xFFFFB0B0, 4),
                body("screen.lobotocraft.welcome.visual.3", 0xFFFFB0B0, 8),
                heading("screen.lobotocraft.welcome.tips.heading", 0xFFFFC46B),
                body("screen.lobotocraft.welcome.tips.1", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.tips.2", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.tips.3", 0xFFE6E6E6, 3),
                body("screen.lobotocraft.welcome.tips.4", 0xFFE6E6E6, 8),
                body("screen.lobotocraft.welcome.tips.5", 0xFFFFC46B, 0)
        );
    }

    private static NoticeBlock heading(String key, int color) {
        return new NoticeBlock(Component.translatable(key), color, true, 0);
    }

    private static NoticeBlock body(String key, int color, int gap) {
        return new NoticeBlock(Component.translatable(key), color, false, gap);
    }

    private record NoticeBlock(Component text, int color, boolean heading, int gap) {
    }

    private record RenderedLine(FormattedCharSequence text, int color, int height) {
    }
}
