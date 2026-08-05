package com.wzz.lobotocraft.client.ui;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 脑叶公司统一 UI 绘制器 —— 全静态方法（DESIGN_PLAN.md 1.2~1.7）。
 * 所有贴图用法均先经 {@link #textureExists} 探测 `theme/` 目录，
 * 贴图缺失时走程序化绘制降级，任何情况下界面不出现空洞。
 */
public final class LobotomyUiRenderer {

    /** 按钮六态（DESIGN_PLAN 1.3）。 */
    public enum ButtonState { NORMAL, HOVER, PRESSED, SELECTED, DISABLED, DANGER }

    // ===== 主题贴图（1.8 命名约定；不存在则程序化降级） =====
    private static final ResourceLocation TEX_PANEL_FRAME = ResourceUtil.createInstance("textures/gui/theme/panel_frame.png");
    private static final ResourceLocation TEX_BUTTON = ResourceUtil.createInstance("textures/gui/theme/button.png");
    private static final ResourceLocation TEX_BUTTON_HOVER = ResourceUtil.createInstance("textures/gui/theme/button_hover.png");
    private static final ResourceLocation TEX_BUTTON_DISABLED = ResourceUtil.createInstance("textures/gui/theme/button_disabled.png");
    private static final ResourceLocation TEX_BAR_FRAME = ResourceUtil.createInstance("textures/gui/theme/bar_frame.png");
    // 贴图落位前尺寸未知，按九宫格惯例假定；素材落位时如尺寸不同再统一调整此处常量。
    private static final int PANEL_FRAME_TEX_SIZE = 32;
    private static final int BUTTON_TEX_W = 200;
    private static final int BUTTON_TEX_H = 20;
    private static final int BAR_FRAME_TEX_W = 32;
    private static final int BAR_FRAME_TEX_H = 8;

    private static final String ELLIPSIS = "…";
    private static final Map<ResourceLocation, Boolean> TEXTURE_CACHE = new HashMap<>();

    private LobotomyUiRenderer() {
    }

    // ===================== 面板 =====================

    /**
     * 标准深色面板：BG_PANEL 底 + 1px LINE_MID 描边 + 顶边 LINE_BRIGHT 受光线
     * + 四角青白角括号（全 mod 视觉签名）。
     */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_PANEL);
        if (textureExists(TEX_PANEL_FRAME)) {
            drawNineSlice(g, TEX_PANEL_FRAME, x, y, w, h, PANEL_FRAME_TEX_SIZE, PANEL_FRAME_TEX_SIZE, 8);
        } else {
            g.fill(x, y + 1, x + 1, y + h - 1, LobotomyUiTheme.LINE_MID);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, LobotomyUiTheme.LINE_MID);
            g.fill(x, y + h - 1, x + w, y + h, LobotomyUiTheme.LINE_MID);
            g.fill(x, y, x + w, y + 1, LobotomyUiTheme.LINE_BRIGHT);
        }
        drawCorners(g, x - 1, y - 1, w + 2, h + 2, LobotomyUiTheme.CORNER_LEN, LobotomyUiTheme.LINE_ACCENT);
    }

    /**
     * 标准面板头（高 HEADER_H=40）：英文全大写 H1 + 青白短下划线 + 中文 H2 副题 + 底部分隔线。
     */
    public static void drawPanelHeader(GuiGraphics g, Font font, int x, int y, int w,
                                       String titleEn, String subtitleZh) {
        int textX = x + LobotomyUiTheme.PAD;
        g.drawString(font, titleEn, textX, y + 12, LobotomyUiTheme.TEXT_TITLE, false);
        g.fill(textX, y + 24, textX + 24, y + 25, LobotomyUiTheme.LINE_ACCENT);
        if (subtitleZh != null && !subtitleZh.isEmpty()) {
            g.drawString(font, subtitleZh, textX, y + 26, LobotomyUiTheme.TEXT_MUTED, false);
        }
        drawDivider(g, x + 1, x + w - 1, y + LobotomyUiTheme.HEADER_H - 1);
    }

    /** 四角"[ ]"式角括号装饰：每角水平+垂直两段 1px 线。 */
    public static void drawCorners(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
        int right = x + w;
        int bottom = y + h;
        // 左上
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        // 右上
        g.fill(right - len, y, right, y + 1, color);
        g.fill(right - 1, y, right, y + len, color);
        // 左下
        g.fill(x, bottom - 1, x + len, bottom, color);
        g.fill(x, bottom - len, x + 1, bottom, color);
        // 右下
        g.fill(right - len, bottom - 1, right, bottom, color);
        g.fill(right - 1, bottom - len, right, bottom, color);
    }

    /** 内嵌区域：BG_INSET 底 + 1px LINE_FAINT 边（列表容器/输入框外包/成本卡）。 */
    public static void drawInsetBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_INSET);
        g.fill(x, y, x + w, y + 1, LobotomyUiTheme.LINE_FAINT);
        g.fill(x, y + h - 1, x + w, y + h, LobotomyUiTheme.LINE_FAINT);
        g.fill(x, y + 1, x + 1, y + h - 1, LobotomyUiTheme.LINE_FAINT);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, LobotomyUiTheme.LINE_FAINT);
    }

    /** 1px 暗分隔线。 */
    public static void drawDivider(GuiGraphics g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, LobotomyUiTheme.LINE_FAINT);
    }

    // ===================== 控件 =====================

    /**
     * 按钮六态绘制（DESIGN_PLAN 1.3 表）。文字水平垂直居中，shadow=false。
     */
    public static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  Component label, ButtonState state) {
        ResourceLocation tex = switch (state) {
            case HOVER, PRESSED, SELECTED -> TEX_BUTTON_HOVER;
            case DISABLED -> TEX_BUTTON_DISABLED;
            default -> TEX_BUTTON;
        };
        boolean textured = textureExists(tex);
        if (textured) {
            drawNineSlice(g, tex, x, y, w, h, BUTTON_TEX_W, BUTTON_TEX_H, 4);
        }
        switch (state) {
            case NORMAL -> {
                if (!textured) {
                    g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_INSET);
                    drawOutline(g, x, y, w, h, LobotomyUiTheme.LINE_MID);
                }
            }
            case HOVER -> {
                if (!textured) {
                    g.fill(x, y, x + w, y + h, 0xFF1B242A);
                    drawOutline(g, x, y, w, h, LobotomyUiTheme.LINE_ACCENT);
                }
                // 左右两角 3px 角标
                g.fill(x, y, x + 3, y + 1, LobotomyUiTheme.LINE_ACCENT);
                g.fill(x, y, x + 1, y + 3, LobotomyUiTheme.LINE_ACCENT);
                g.fill(x + w - 3, y, x + w, y + 1, LobotomyUiTheme.LINE_ACCENT);
                g.fill(x + w - 1, y, x + w, y + 3, LobotomyUiTheme.LINE_ACCENT);
            }
            case PRESSED, SELECTED -> {
                if (!textured) {
                    g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_INSET);
                }
                g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_ROW_SELECTED);
                drawOutline(g, x, y, w, h, LobotomyUiTheme.LINE_ACCENT);
                g.fill(x + 1, y + h - 2, x + w - 1, y + h, LobotomyUiTheme.ACCENT);
            }
            case DISABLED -> {
                if (!textured) {
                    g.fill(x, y, x + w, y + h, 0xFF0C0C11);
                    drawOutline(g, x, y, w, h, LobotomyUiTheme.LINE_FAINT);
                }
            }
            case DANGER -> {
                g.fill(x, y, x + w, y + h, 0xFF221012);
                drawOutline(g, x, y, w, h, LobotomyUiTheme.ALERT_RED);
            }
        }
        int textColor = switch (state) {
            case NORMAL -> LobotomyUiTheme.TEXT_BODY;
            case HOVER -> LobotomyUiTheme.TEXT_TITLE;
            case PRESSED, SELECTED -> LobotomyUiTheme.ACCENT;
            case DISABLED -> LobotomyUiTheme.TEXT_DISABLED;
            case DANGER -> 0xFFE09090;
        };
        String text = fitText(font, label.getString(), Math.max(8, w - 6));
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    /** 标签页：激活=青白 2px 下划线 + ACCENT 文字；悬停提亮。 */
    public static void drawTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                               String label, boolean active, boolean hovered) {
        g.fill(x, y, x + w, y + h, active ? LobotomyUiTheme.BG_INSET : LobotomyUiTheme.BG_ROW_ODD);
        if (hovered && !active) {
            g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_ROW_HOVER);
        }
        g.fill(x, y + h - 1, x + w, y + h, LobotomyUiTheme.LINE_FAINT);
        if (active) {
            g.fill(x, y + h - 2, x + w, y + h, LobotomyUiTheme.ACCENT);
        }
        int color = active ? LobotomyUiTheme.ACCENT
                : hovered ? LobotomyUiTheme.TEXT_BODY : LobotomyUiTheme.TEXT_MUTED;
        String text = fitText(font, label, Math.max(8, w - 6));
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, false);
    }

    /** 小状态胶囊：底=color 20% alpha，文字=color。高 12px，左右内边距 4px。 */
    public static void drawTag(GuiGraphics g, Font font, int x, int y,
                               String text, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 12, withAlpha(color, 0.125F));
        g.drawString(font, text, x + 4, y + 2, color, false);
    }

    // ===================== 条 =====================

    /**
     * 通用单条：BAR_TRACK 底 + 1px LINE_FAINT 描边；h ≥ 8 时填充顶部 1/3 叠白光带。
     */
    public static void drawBar(GuiGraphics g, int x, int y, int w, int h,
                               float value, float max, int fillColor) {
        g.fill(x, y, x + w, y + h, LobotomyUiTheme.LINE_FAINT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LobotomyUiTheme.BAR_TRACK);
        int fill = Mth.clamp(Math.round((w - 2) * value / Math.max(1.0F, max)), 0, w - 2);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, fillColor);
            if (h >= 8) {
                g.fill(x + 1, y + 1, x + 1 + fill, y + 1 + (h - 2) / 3, 0x33FFFFFF);
            }
        }
        if (textureExists(TEX_BAR_FRAME)) {
            drawNineSlice(g, TEX_BAR_FRAME, x - 1, y - 1, w + 2, h + 2, BAR_FRAME_TEX_W, BAR_FRAME_TEX_H, 3);
        }
    }

    /** "故障/中断"条：ALERT_RED_DIM 7px 段 + 5px 空隙条纹（沿用 FacilityHud 反应堆故障画法）。 */
    public static void drawBrokenBar(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, LobotomyUiTheme.LINE_FAINT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LobotomyUiTheme.BAR_TRACK);
        for (int sx = x + 1; sx < x + w - 1; sx += 12) {
            g.fill(sx, y + 1, Math.min(sx + 7, x + w - 1), y + h - 1, LobotomyUiTheme.ALERT_RED_DIM);
        }
    }

    /**
     * 员工 HP/SP 双细条：各高 3px，间隔 1px，总高 7px；HP 红、SP 白（<25% 转警戒红）；
     * 左端 1px LINE_MID 竖线封头。
     */
    public static void drawDualBars(GuiGraphics g, int x, int y, int w,
                                    float hp, float maxHp, float sp, float maxSp) {
        g.fill(x, y, x + 1, y + 7, LobotomyUiTheme.LINE_MID);
        int barX = x + 1;
        int barW = Math.max(1, w - 1);
        int spColor = sp / Math.max(1.0F, maxSp) < 0.25F
                ? LobotomyUiTheme.BAR_SP_LOW : LobotomyUiTheme.BAR_SP;
        drawThinBar(g, barX, y, barW, hp, maxHp, LobotomyUiTheme.BAR_HP);
        drawThinBar(g, barX, y + 4, barW, sp, maxSp, spColor);
    }

    private static void drawThinBar(GuiGraphics g, int x, int y, int w,
                                    float value, float max, int fillColor) {
        g.fill(x, y, x + w, y + 3, LobotomyUiTheme.BAR_TRACK);
        int fill = Mth.clamp(Math.round(w * value / Math.max(1.0F, max)), 0, w);
        if (fill > 0) {
            g.fill(x, y, x + fill, y + 3, fillColor);
            g.fill(x, y, x + fill, y + 1, 0x40FFFFFF);
        }
    }

    /** 滚动条：宽 2px，LINE_MID 轨 + ACCENT 滑块；内容不超出可视高时不绘制。 */
    public static void drawScrollbar(GuiGraphics g, int x, int top, int bottom,
                                     int contentHeight, int scroll) {
        int viewHeight = bottom - top;
        if (viewHeight <= 0 || contentHeight <= viewHeight) return;
        g.fill(x, top, x + 2, bottom, LobotomyUiTheme.LINE_MID);
        int thumbHeight = Math.max(8, viewHeight * viewHeight / contentHeight);
        int maxScroll = contentHeight - viewHeight;
        int thumbY = top + (viewHeight - thumbHeight) * Mth.clamp(scroll, 0, maxScroll) / maxScroll;
        g.fill(x, thumbY, x + 2, thumbY + thumbHeight, LobotomyUiTheme.ACCENT);
    }

    // ===================== 列表 =====================

    /**
     * 列表行：斑马底 + 悬停/选中叠加 + 底部分隔线；选中时附左缘 2px ACCENT 竖条。
     */
    public static void drawRow(GuiGraphics g, int x, int y, int w, int h,
                               int index, boolean hovered, boolean selected) {
        g.fill(x, y, x + w, y + h,
                index % 2 == 0 ? LobotomyUiTheme.BG_ROW_EVEN : LobotomyUiTheme.BG_ROW_ODD);
        if (hovered) {
            g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_ROW_HOVER);
        }
        if (selected) {
            g.fill(x, y, x + w, y + h, LobotomyUiTheme.BG_ROW_SELECTED);
            g.fill(x, y, x + 2, y + h, LobotomyUiTheme.ACCENT);
        }
        g.fill(x, y + h - 1, x + w, y + h, LobotomyUiTheme.LINE_FAINT);
    }

    // ===================== 工具 =====================

    /** 替换 ARGB 颜色的 alpha 通道（0.0~1.0）。 */
    public static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 超宽截断，使用单字符省略号 "…"。 */
    public static String fitText(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /** 整图缩放绘制（收编 FacilityHud/CarmenConversationOverlay 的私有实现）。 */
    public static void blitScaled(GuiGraphics g, ResourceLocation tex, int x, int y,
                                  int w, int h, int texW, int texH) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale((float) w / texW, (float) h / texH, 1.0F);
        g.blit(tex, 0, 0, 0, 0, texW, texH, texW, texH);
        g.pose().popPose();
    }

    /** 九宫格绘制：四角 1:1，四边与中心拉伸；border 为切片边宽。 */
    public static void drawNineSlice(GuiGraphics g, ResourceLocation tex, int x, int y,
                                     int w, int h, int texW, int texH, int border) {
        int b = Math.min(border, Math.min(Math.min(w, h) / 2, Math.min(texW, texH) / 2));
        if (b <= 0) {
            g.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
            return;
        }
        int innerW = w - 2 * b;
        int innerH = h - 2 * b;
        int texInnerW = texW - 2 * b;
        int texInnerH = texH - 2 * b;
        // 四角
        g.blit(tex, x, y, b, b, 0, 0, b, b, texW, texH);
        g.blit(tex, x + w - b, y, b, b, texW - b, 0, b, b, texW, texH);
        g.blit(tex, x, y + h - b, b, b, 0, texH - b, b, b, texW, texH);
        g.blit(tex, x + w - b, y + h - b, b, b, texW - b, texH - b, b, b, texW, texH);
        // 四边
        if (innerW > 0) {
            g.blit(tex, x + b, y, innerW, b, b, 0, texInnerW, b, texW, texH);
            g.blit(tex, x + b, y + h - b, innerW, b, b, texH - b, texInnerW, b, texW, texH);
        }
        if (innerH > 0) {
            g.blit(tex, x, y + b, b, innerH, 0, b, b, texInnerH, texW, texH);
            g.blit(tex, x + w - b, y + b, b, innerH, texW - b, b, b, texInnerH, texW, texH);
        }
        // 中心
        if (innerW > 0 && innerH > 0) {
            g.blit(tex, x + b, y + b, innerW, innerH, b, b, texInnerW, texInnerH, texW, texH);
        }
    }

    /** 贴图存在性探测（ResourceUtil.exists + 缓存），用于 theme/ 贴图降级判定。 */
    public static boolean textureExists(ResourceLocation tex) {
        return TEXTURE_CACHE.computeIfAbsent(tex, ResourceUtil::exists);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
