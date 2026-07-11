package com.wzz.lobotocraft.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 考验用的自定义标题（替代原版 title）。
 *
 * 结构（从上到下）：
 *   半透明黑色横幅 + 左右两侧的斜纹警戒条
 *   ├─ 顶部小字（例：紫罗兰的黎明 90%）
 *   ├─ 中间大标题（例：理解的果实）
 *   └─ 底部小字，比顶部大一些（例：有朝一日……）
 *
 * 淡入 / 停留 / 淡出 的计时与原版 title 一致（Gui#render 的算法）。
 * 服务端用 OrdealTitlePacket 触发 {@link #show}。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class OrdealTitleOverlay {

    /* ======== 可调的排版参数 ======== */
    /** 横幅高度 */
    private static final int BAND_HEIGHT = 108;
    /** 横幅中心相对屏幕中心的偏移（负数=偏上） */
    private static final int BAND_Y_OFFSET = -24;
    /** 左右警戒条宽度 */
    private static final int STRIPE_BAR_WIDTH = 18;
    /** 单根斜纹的宽度 */
    private static final int STRIPE_WIDTH = 7;
    /** 斜纹滚动速度（0 = 不滚动） */
    private static final float STRIPE_SCROLL_SPEED = 0.03f;
    /** 背景黑色的不透明度 */
    private static final float BACKGROUND_ALPHA = 0.72f;

    private static final float TITLE_SCALE = 2.4f;
    private static final float SUBTITLE_SCALE = 1.15f;
    private static final float TOP_SCALE = 1.0f;

    /* ======== 状态 ======== */
    private static Component topText = Component.empty();
    private static Component titleText = Component.empty();
    private static Component subtitleText = Component.empty();
    private static int themeColor = 0xC77DFF;

    private static int fadeIn = 10;
    private static int stay = 80;
    private static int fadeOut = 20;
    private static int time = 0;

    public static void show(Component top, Component title, Component subtitle,
                            int themeColor, int fadeIn, int stay, int fadeOut) {
        OrdealTitleOverlay.topText = top == null ? Component.empty() : top;
        OrdealTitleOverlay.titleText = title == null ? Component.empty() : title;
        OrdealTitleOverlay.subtitleText = subtitle == null ? Component.empty() : subtitle;
        OrdealTitleOverlay.themeColor = themeColor;
        OrdealTitleOverlay.fadeIn = Math.max(1, fadeIn);
        OrdealTitleOverlay.stay = Math.max(0, stay);
        OrdealTitleOverlay.fadeOut = Math.max(1, fadeOut);
        OrdealTitleOverlay.time = OrdealTitleOverlay.fadeIn + OrdealTitleOverlay.stay + OrdealTitleOverlay.fadeOut;
    }

    public static void clear() {
        time = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (!mc.isPaused() && time > 0) {
            time--;
        }
    }

    /**
     * 注册用：RegisterGuiOverlaysEvent 里 event.registerAboveAll("ordeal_title", OrdealTitleOverlay::render);
     */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (time <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int alpha = computeAlpha(partialTick);
        if (alpha <= 4) return;

        Font font = mc.font;
        int bandTop = height / 2 + BAND_Y_OFFSET - BAND_HEIGHT / 2;
        int bandBottom = bandTop + BAND_HEIGHT;
        int centerX = width / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 半透明黑色横幅
        int bgAlpha = (int) (alpha * BACKGROUND_ALPHA);
        graphics.fill(0, bandTop, width, bandBottom, bgAlpha << 24);

        // 上下的主题色描边
        int edge = withAlpha(themeColor, (int) (alpha * 0.85f));
        graphics.fill(0, bandTop, width, bandTop + 1, edge);
        graphics.fill(0, bandBottom - 1, width, bandBottom, edge);

        // 左右的斜纹警戒条
        float scroll = STRIPE_SCROLL_SPEED == 0f ? 0f
                : (mc.level.getGameTime() + partialTick) * STRIPE_SCROLL_SPEED * 20f % (STRIPE_WIDTH * 2);
        drawHazardStripes(graphics, 0, bandTop, STRIPE_BAR_WIDTH, BAND_HEIGHT, alpha, scroll);
        drawHazardStripes(graphics, width - STRIPE_BAR_WIDTH, bandTop, STRIPE_BAR_WIDTH, BAND_HEIGHT, alpha, scroll);

        // 文字
        int textAlpha = alpha << 24;
        int topY = bandTop + 12;
        int titleY = bandTop + 34;
        int subtitleY = bandBottom - 26;

        drawScaledCenteredString(graphics, font, topText, centerX, topY, TOP_SCALE,
                (themeColor & 0xFFFFFF) | textAlpha);
        drawScaledCenteredString(graphics, font, titleText, centerX, titleY, TITLE_SCALE,
                (themeColor & 0xFFFFFF) | textAlpha);
        drawScaledCenteredString(graphics, font, subtitleText, centerX, subtitleY, SUBTITLE_SCALE,
                0xFFFFFF | textAlpha);

        RenderSystem.disableBlend();
    }

    /** 与原版 Gui#render 的 title 淡入淡出完全一致 */
    private static int computeAlpha(float partialTick) {
        float remaining = time - partialTick;
        int alpha = 255;
        if (time > fadeOut + stay) {
            float elapsed = (fadeIn + stay + fadeOut) - remaining;
            alpha = (int) (elapsed * 255.0F / fadeIn);
        }
        if (time <= fadeOut) {
            alpha = (int) (remaining * 255.0F / fadeOut);
        }
        return Mth.clamp(alpha, 0, 255);
    }

    /**
     * 斜纹警戒条：在裁剪区域里把 PoseStack 旋转 45°，画一排竖条，
     * 旋转之后就是斜条纹。
     */
    private static void drawHazardStripes(GuiGraphics graphics, int x, int y, int barWidth, int barHeight,
                                          int alpha, float scroll) {
        int stripeColor = withAlpha(themeColor, alpha);
        int gapColor = withAlpha(0x000000, (int) (alpha * 0.85f));

        graphics.enableScissor(x, y, x + barWidth, y + barHeight);
        graphics.fill(x, y, x + barWidth, y + barHeight, gapColor);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));

        int span = barWidth + barHeight + STRIPE_WIDTH * 4;
        for (int i = -span; i <= span; i += STRIPE_WIDTH * 2) {
            int sx = i + (int) scroll;
            graphics.fill(sx, -span, sx + STRIPE_WIDTH, span, stripeColor);
        }

        pose.popPose();
        graphics.disableScissor();
    }

    private static void drawScaledCenteredString(GuiGraphics graphics, Font font, Component text,
                                                 int centerX, int y, float scale, int color) {
        if (text.getString().isEmpty()) return;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, y, 0);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        pose.popPose();
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}