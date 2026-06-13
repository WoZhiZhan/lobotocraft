package com.wzz.lobotocraft.client.screen.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 界面自适应缩放工具("只缩不放")。
 *
 * 问题:部分界面使用固定像素尺寸的面板/布局。当玩家调大"界面尺寸"(GUI Scale)后,
 * 缩放后的逻辑分辨率变小,固定尺寸内容放不下,导致显示错位或超出屏幕。
 *
 * 方案:
 *  - 每个界面声明自己的"设计最小尺寸"(minW x minH);
 *  - 实际逻辑分辨率足够时 scale = 1,渲染与原来完全一致(不破坏现有显示样式);
 *  - 放不下时整体围绕屏幕中心等比缩小,所有元素相对位置不变;
 *  - 鼠标坐标做同样的逆变换,保证悬停与点击判定和渲染一致(位置不会错乱)。
 *
 * 用法(面板居中型界面):
 *  render() 开头:
 *      uiScale = AutoScaleHelper.fitScale(this.width, this.height, MIN_W, MIN_H);
 *      int mx = AutoScaleHelper.mouseX(mouseX, this.width, uiScale);
 *      int my = AutoScaleHelper.mouseY(mouseY, this.height, uiScale);
 *      AutoScaleHelper.begin(graphics, this.width, this.height, uiScale);
 *      ... 原有渲染逻辑(悬停判定使用 mx/my) ...
 *      AutoScaleHelper.end(graphics, uiScale);
 *  鼠标事件(mouseClicked 等)开头对坐标做同样变换。
 */
public final class AutoScaleHelper {

    private AutoScaleHelper() {}

    /** 计算适配比例:逻辑分辨率能容纳设计尺寸时返回 1(不改变任何显示),否则返回等比缩小值 */
    public static float fitScale(int width, int height, int minWidth, int minHeight) {
        if (width <= 0 || height <= 0) return 1f;
        float s = Math.min(1f, Math.min(width / (float) minWidth, height / (float) minHeight));
        return s <= 0f ? 1f : s;
    }

    /** 围绕屏幕中心开始等比缩放渲染(scale=1 时不做任何事) */
    public static void begin(GuiGraphics graphics, int width, int height, float scale) {
        if (scale >= 1f) return;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-width / 2f, -height / 2f, 0);
    }

    /** 结束缩放渲染 */
    public static void end(GuiGraphics graphics, float scale) {
        if (scale >= 1f) return;
        graphics.pose().popPose();
    }

    /** 鼠标X逆变换(围绕中心),与 begin 的渲染变换保持一致 */
    public static int mouseX(int mouseX, int width, float scale) {
        if (scale >= 1f) return mouseX;
        return (int) (width / 2f + (mouseX - width / 2f) / scale);
    }

    public static int mouseY(int mouseY, int height, float scale) {
        if (scale >= 1f) return mouseY;
        return (int) (height / 2f + (mouseY - height / 2f) / scale);
    }

    public static double mouseX(double mouseX, int width, float scale) {
        if (scale >= 1f) return mouseX;
        return width / 2f + (mouseX - width / 2f) / scale;
    }

    public static double mouseY(double mouseY, int height, float scale) {
        if (scale >= 1f) return mouseY;
        return height / 2f + (mouseY - height / 2f) / scale;
    }

    // ==================== 全屏型界面(以原点缩放 + 虚拟分辨率) ====================

    /**
     * 全屏型界面(背景铺满屏幕、内容用固定坐标)使用:
     * 以原点缩放,布局改用虚拟分辨率 virtualWidth/Height,缩小后正好铺满实际屏幕。
     */
    public static void beginFullscreen(GuiGraphics graphics, float scale) {
        if (scale >= 1f) return;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
    }

    public static int virtualWidth(int width, float scale) {
        if (scale >= 1f) return width;
        return (int) Math.ceil(width / scale);
    }

    public static int virtualHeight(int height, float scale) {
        if (scale >= 1f) return height;
        return (int) Math.ceil(height / scale);
    }

    /** 全屏型鼠标逆变换(原点缩放) */
    public static int mouseFullscreen(int mouse, float scale) {
        if (scale >= 1f) return mouse;
        return (int) (mouse / scale);
    }

    public static double mouseFullscreen(double mouse, float scale) {
        if (scale >= 1f) return mouse;
        return mouse / scale;
    }
}
