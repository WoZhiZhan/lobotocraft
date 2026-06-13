package com.wzz.lobotocraft.client.alert;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class EscapeOverlayRenderer {

    private static final ResourceLocation[] ICONS = {
            ResourceUtil.createInstance("textures/gui/escape_alert/icon_1.png"),
            ResourceUtil.createInstance("textures/gui/escape_alert/icon_2.png"),
            ResourceUtil.createInstance("textures/gui/escape_alert/icon_3.png"),
    };

    private static final int DRAW_H = 128; // 只控制显示高度，宽度自动按比例算

    // 缓存每张图的真实尺寸，int[]{w, h}，null 表示还没读取
    private static final int[][] texSizes = new int[ICONS.length][];

    /**
     * 读取 PNG 真实像素尺寸并缓存，失败时返回 {DRAW_H, DRAW_H} 兜底
     */
    private static int[] getTexSize(int index) {
        if (texSizes[index] != null) return texSizes[index];
        try (var stream = Minecraft.getInstance()
                .getResourceManager()
                .open(ICONS[index]);
             var image = NativeImage.read(stream)) {
            texSizes[index] = new int[]{ image.getWidth(), image.getHeight() };
        } catch (Exception e) {
            texSizes[index] = new int[]{ DRAW_H, DRAW_H }; // 兜底正方形
        }
        return texSizes[index];
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().isPaused()) return;
        EscapeAlertManager.getInstance().tick();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        EscapeAlertManager manager = EscapeAlertManager.getInstance();
        if (!manager.isFlashVisible()) return;

        int iconIndex = manager.flashIconIndex;
        if (iconIndex < 0 || iconIndex >= ICONS.length) return;

        int[] size  = getTexSize(iconIndex);
        int   texW  = size[0];
        int   texH  = size[1];

        // ★ 按真实比例换算显示宽度
        int drawH = DRAW_H;
        int drawW = texH == 0 ? drawH : (drawH * texW / texH);

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int x = (screenW - drawW) / 2;
        int y = (screenH - drawH) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 显示尺寸和贴图真实尺寸分别传入，MC 自动正确采样
        graphics.blit(ICONS[iconIndex], x, y, 0, 0, drawW, drawH, texW, texH);

        RenderSystem.disableBlend();
    }
}