package com.wzz.lobotocraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.wzz.lobotocraft.init.ModShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

public class ScreenDistortionEffect {

    public static void renderScreenDistortion(GuiGraphics guiGraphics, float intensity) {
        if (ModShaders.SCREEN_DISTORTION_SHADER == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        try {
            ModShaders.screenDistortionIntensity.set(Math.max(0.1f, intensity));

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShaderTexture(0, mc.getMainRenderTarget().getColorTextureId());
            RenderSystem.setShader(() -> ModShaders.SCREEN_DISTORTION_SHADER);

            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            builder.vertex(matrix, 0, 0, 0).uv(0, 1).endVertex();           // 左上
            builder.vertex(matrix, 0, guiHeight, 0).uv(0, 0).endVertex();   // 左下
            builder.vertex(matrix, guiWidth, guiHeight, 0).uv(1, 0).endVertex(); // 右下
            builder.vertex(matrix, guiWidth, 0, 0).uv(1, 1).endVertex();    // 右上

            BufferUploader.drawWithShader(builder.end());

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();

            poseStack.popPose();

        } catch (Exception e) {
            poseStack.popPose();
        }
    }
}