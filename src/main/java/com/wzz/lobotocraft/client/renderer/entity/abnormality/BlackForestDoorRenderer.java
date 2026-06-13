package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.renderer.entity.GeoEntityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public class BlackForestDoorRenderer<T extends EntityBlackForestDoor> extends GeoEntityRenderer<T> {

    private static final RenderType GLOW_RING = RenderType.create(
            "glow_ring",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .setDepthTestState(RenderType.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    public BlackForestDoorRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, 1.2f, 1f);
    }


    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        renderGlowRing(poseStack, bufferSource, entity, partialTick);
    }

    private void renderGlowRing(PoseStack poseStack, MultiBufferSource bufferSource, T entity, float partialTick) {
        poseStack.pushPose();

        float offsetX = 0.0f;
        float offsetY = 1.35f;
        float offsetZ = 0.0f;
        poseStack.translate(offsetX, offsetY, offsetZ);

        float outerWidth = 0.85f;
        float outerHeight = 1.5f;
        float innerWidth = 0.35f;
        float innerHeight = 0.5f;

        float r = 1.0f;
        float g = 0.85f;
        float b = 0.0f;
        float alpha = 0.9f;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(GLOW_RING);

        int segments = 120;

        float tick = (entity.tickCount + partialTick) * 0.05f;

        for (int i = 0; i <= segments; i++) {
            double angle = (i / (double)segments) * Math.PI * 2;

            float noiseOuter = 1.0f +
                    0.08f * (float)Math.sin(angle * 5 + tick * 2.0f) +
                    0.05f * (float)Math.sin(angle * 11 - tick * 1.3f) +
                    0.06f * (float)Math.cos(angle * 8 + tick * 1.7f) +
                    0.03f * (float)Math.sin(angle * 17 + tick * 3.0f);

            float noiseInner = 1.0f +
                    0.06f * (float)Math.sin(angle * 7 + tick * 1.5f) +
                    0.04f * (float)Math.cos(angle * 9 - tick * 2.1f) +
                    0.05f * (float)Math.sin(angle * 13 + tick * 2.8f);

            float ox = (float)(Math.cos(angle) * outerWidth * noiseOuter);
            float oy = (float)(Math.sin(angle) * outerHeight * noiseOuter);
            float ix = (float)(Math.cos(angle) * innerWidth * noiseInner);
            float iy = (float)(Math.sin(angle) * innerHeight * noiseInner);

            float flicker = 0.8f + 0.2f * (float)Math.sin(angle * 3 + tick * 4.0f);

            consumer.vertex(matrix, ox, oy, 0.0f).color(r * flicker, g * flicker, b, alpha).endVertex();
            consumer.vertex(matrix, ix, iy, 0.0f).color(r * flicker * 0.2f, g * flicker * 0.2f, b * 0.3f, 0.0f).endVertex();
        }

        poseStack.popPose();
    }
}