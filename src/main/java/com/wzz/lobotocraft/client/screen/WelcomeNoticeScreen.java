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
        super(Component.literal("LobotoCraft 实验性重置版"));
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
        graphics.drawCenteredString(font, "致歉、公告与声明", width / 2, panelY + 16, 0xFFFFC46B);
        graphics.drawCenteredString(font, "LobotoCraft 实验性重置版", width / 2, panelY + 36, 0xFFE6E6E6);

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
        graphics.drawCenteredString(font, "确认后本提示将在当前存档中不再显示", width / 2,
                viewportBottom + 12, 0xFF999999);

        int buttonWidth = Math.min(300, panelWidth - 36);
        int buttonX = width / 2 - buttonWidth / 2;
        int buttonY = panelBottom - 34;
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, 24);
        graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 24,
                hovered ? 0xFF3B2B16 : 0xFF211A11);
        outline(graphics, buttonX, buttonY, buttonWidth, 24, hovered ? 0xFFFFD27A : 0xFFB87824);
        graphics.drawCenteredString(font, "我已阅读，进入设施", width / 2, buttonY + 8,
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
                heading("致歉", 0xFFFFC46B),
                body("首先，我需要为 1.0 版本较差的游玩体验向各位玩家郑重道歉。", 0xFFE6E6E6, 4),
                body("1.0 版本在完成度、界面设计、机制合理性、多人同步、性能与稳定性等方面均存在明显不足，包括全设施异常生成、天数与能源机制混乱、引导缺失、界面简陋，以及多项严重影响正常游玩的缺陷。", 0xFFE6E6E6, 4),
                body("这些问题确实损害了玩家体验。玩家对此提出批评与不满完全合理。问题主要源于早期开发阶段缺乏完整的架构规划、充分测试与质量控制，相应责任应由开发方承担，而不应由玩家通过反复重进存档、修改配置或自行排查问题来弥补。", 0xFFE6E6E6, 4),
                body("本次实验性重置不是为了否认或掩盖旧版本的问题，而是希望认真修正这些不足。当前版本仍无法保证不存在缺陷，但我们会继续依据测试结果与玩家反馈改善稳定性、完成度与整体体验。", 0xFFE6E6E6, 8),

                heading("版本公告", 0xFFFFC46B),
                body("本次实验性重置对原有内容进行了大规模重构，主要包括：", 0xFFE6E6E6, 2),
                body("1. 重构设施运行机制。异想体与事件实体将根据已开放部门生成，不再无条件影响整个设施。", 0xFFE6E6E6, 2),
                body("2. 统一多人游戏中的设施天数、能源进度与主要事件状态，减少客户端之间的进度差异。", 0xFFE6E6E6, 2),
                body("3. 重制能源获取、每日结算、逆卡巴拉能量推进、逆流与考验机制，使其更加接近《脑叶公司》的整体流程。", 0xFFE6E6E6, 2),
                body("4. 大幅重制 Mod 界面，统一视觉风格，并改善工作选择、员工管理、设施监控与异想体信息等页面。", 0xFFE6E6E6, 2),
                body("5. 补充异想体故事与档案内容，可在异想体详细信息中查看。", 0xFFE6E6E6, 2),
                body("6. 加入 AI 员工系统。员工能够参与工作、移动、乘坐电梯与镇压，并可在完成配置后接入大语言模型，提供更深入的角色化互动体验。", 0xFFE6E6E6, 6),
                body("以上仅为主要改动，实际修复、优化与新增内容不限于上述项目。", 0xFFBDBDBD, 8),

                heading("作品声明", 0xFFFFC46B),
                body("1. LobotoCraft 的核心 Mod 为独立开发内容，可脱离附带的整合环境运行。本作品不属于传统意义上仅由现有 Mod 组合而成的整合包。", 0xFFE6E6E6, 3),
                body("2. 当前版本仍处于实验阶段，可能存在影响正常游玩的缺陷，包括员工寻路异常、多人状态不同步、实体行为异常及界面显示问题。", 0xFFE6E6E6, 3),
                body("3. 当前版本不代表最终品质。游戏内容、机制、数值、界面与视觉表现均可能在后续版本中继续调整或重制。", 0xFFE6E6E6, 3),
                body("4. AI 员工的大语言模型功能属于可选功能。未进行相关配置时，不影响基础员工系统的使用。", 0xFFE6E6E6, 8),

                heading("视觉安全警告", 0xFFFF6666),
                body("核心抑制及部分异常事件包含闪烁、画面扭曲、色彩变化和其他强烈视觉效果，可能引发光敏性癫痫或造成身体不适。", 0xFFFFB0B0, 4),
                body("如游玩过程中出现头晕、恶心、视线异常或其他不适，请立即停止游玩，并在必要时咨询医生。", 0xFFFFB0B0, 4),
                body("你可以在“视觉警告”设置中关闭核心抑制的特殊视觉效果。对闪烁画面较为敏感的玩家，建议在开始游玩前完成相关设置。", 0xFFFFB0B0, 8),

                heading("游玩提示", 0xFFFFC46B),
                body("• 受部分历史资源兼容问题影响，请确保 Minecraft 的“音乐”音量高于 0，否则部分背景音乐可能无法正常播放。", 0xFFE6E6E6, 3),
                body("• 推荐使用 2 倍 GUI 缩放进行游玩；其他缩放比例可能因屏幕分辨率不同而产生显示差异。", 0xFFE6E6E6, 3),
                body("• 建议定期备份重要存档。多人游戏应以服务端保存的设施数据为准。", 0xFFE6E6E6, 3),
                body("• 如遇问题，请尽量保留日志、存档、复现步骤及相关截图，以便定位和修复。", 0xFFE6E6E6, 8),
                body("感谢你阅读以上内容，也感谢所有参与测试、反馈问题并帮助本作品继续完善的玩家。", 0xFFFFC46B, 0)
        );
    }

    private static NoticeBlock heading(String text, int color) {
        return new NoticeBlock(Component.literal(text), color, true, 0);
    }

    private static NoticeBlock body(String text, int color, int gap) {
        return new NoticeBlock(Component.literal(text), color, false, gap);
    }

    private record NoticeBlock(Component text, int color, boolean heading, int gap) {
    }

    private record RenderedLine(FormattedCharSequence text, int color, int height) {
    }
}
