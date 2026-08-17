package com.wzz.lobotocraft.debug.prototype.queenlaser;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wzz.lobotocraft.init.ModShaders;
import com.wzz.lobotocraft.client.shader.CCShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
final class QueenLaserPrototypePostEffect {
    private static TextureTarget sceneCopy;

    private QueenLaserPrototypePostEffect() {
    }

    static void render(GuiGraphics guiGraphics, float intensity) {
        if (ModShaders.QUEEN_LASER_MASK_SHADER == null || intensity <= 0.001F) return;
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        ensureTarget(main.width, main.height);
        if (sceneCopy == null) return;

        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        PoseStack poseStack = guiGraphics.pose();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                    0, 0, sceneCopy.width, sceneCopy.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            main.bindWrite(true);
            RenderSystem.enableBlend();
            ModShaders.queenLaserMaskIntensity.set(intensity);
            ModShaders.queenLaserMaskTexelSize.set(1.0F / Math.max(1, main.width),
                    1.0F / Math.max(1, main.height));

            drawFullscreen(poseStack, guiWidth, guiHeight, sceneCopy.getColorTextureId(),
                    ModShaders.QUEEN_LASER_MASK_SHADER);
        } finally {
            main.bindWrite(true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    static void release() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
    }

    private static void ensureTarget(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (sceneCopy.width != width || sceneCopy.height != height) {
            sceneCopy.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void drawFullscreen(PoseStack poseStack, int width, int height, int textureId,
                                       CCShaderInstance shader) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(() -> shader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var matrix = poseStack.last().pose();
        builder.vertex(matrix, 0.0F, 0.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        builder.vertex(matrix, 0.0F, height, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(matrix, width, height, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(matrix, width, 0.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }
}
