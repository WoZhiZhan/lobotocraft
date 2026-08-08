package com.wzz.lobotocraft.event.listener;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ShockwaveEffectManager;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class ShockwaveRenderer {
    private static final int SEGMENTS = 64;
    private static final double MAX_RENDER_DISTANCE = 192.0D;
    private static final float[] UNIT_X = new float[SEGMENTS + 1];
    private static final float[] UNIT_Z = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i / (double) SEGMENTS * Math.PI * 2.0D;
            UNIT_X[i] = (float) Math.cos(angle);
            UNIT_Z[i] = (float) Math.sin(angle);
        }
    }

    private ShockwaveRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.level != null && !minecraft.isPaused()) {
            ShockwaveEffectManager.tick();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ShockwaveEffectManager.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<ShockwaveEffectManager.Shockwave> shockwaves = ShockwaveEffectManager.getShockwaves();
        if (shockwaves.isEmpty()) return;

        long sectionStarted = ClientPerformanceMonitor.beginSection("shockwave_render");
        try {
            renderShockwaves(event, shockwaves);
        } finally {
            ClientPerformanceMonitor.endSection("shockwave_render", sectionStarted);
        }
    }

    private static void renderShockwaves(RenderLevelStageEvent event,
                                         List<ShockwaveEffectManager.Shockwave> shockwaves) {
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        try {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (ShockwaveEffectManager.Shockwave wave : shockwaves) {
                double dx = wave.x - cameraPosition.x;
                double dy = wave.y - cameraPosition.y;
                double dz = wave.z - cameraPosition.z;
                double renderDistance = MAX_RENDER_DISTANCE + wave.maxRadius;
                if (dx * dx + dy * dy + dz * dz > renderDistance * renderDistance) continue;

                float renderRadius = wave.getRenderRadius(partialTick);
                float renderAlpha = wave.getRenderAlpha(partialTick);
                float red = ((wave.color >> 16) & 0xFF) / 255.0F;
                float green = ((wave.color >> 8) & 0xFF) / 255.0F;
                float blue = (wave.color & 0xFF) / 255.0F;

                poseStack.pushPose();
                poseStack.translate(dx, dy, dz);
                Matrix4f matrix = poseStack.last().pose();

                renderRings(matrix, builder, wave, renderRadius, renderAlpha, red, green, blue);
                renderGroundGlow(matrix, builder, renderRadius, red, green, blue,
                        renderAlpha * 0.3F);
                poseStack.popPose();
            }

            // All visible rings and glows share one buffer submission for this render stage.
            tesselator.end();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void renderRings(Matrix4f matrix, BufferBuilder builder,
                                    ShockwaveEffectManager.Shockwave wave, float renderRadius,
                                    float renderAlpha, float red, float green, float blue) {
        for (int ring = 0; ring < wave.ringCount; ring++) {
            float radius = renderRadius - ring * wave.ringSpacing;
            if (radius <= 0.0F) continue;

            float ringAlpha = renderAlpha * (1.0F - ring * 0.25F);
            float heightTop = 0.6F - ring * 0.15F;
            float heightBottom = -0.3F + ring * 0.1F;

            for (int i = 0; i < SEGMENTS; i++) {
                float x1 = UNIT_X[i] * radius;
                float z1 = UNIT_Z[i] * radius;
                float x2 = UNIT_X[i + 1] * radius;
                float z2 = UNIT_Z[i + 1] * radius;

                builder.vertex(matrix, x1, heightTop, z1).color(red, green, blue, 0.0F).endVertex();
                builder.vertex(matrix, x2, heightTop, z2).color(red, green, blue, 0.0F).endVertex();
                builder.vertex(matrix, x2, heightBottom, z2).color(red, green, blue, ringAlpha).endVertex();
                builder.vertex(matrix, x1, heightBottom, z1).color(red, green, blue, ringAlpha).endVertex();
            }
        }
    }

    private static void renderGroundGlow(Matrix4f matrix, BufferBuilder builder, float radius,
                                         float red, float green, float blue, float alpha) {
        if (radius <= 0.0F || alpha <= 0.0F) return;
        for (int i = 0; i < SEGMENTS; i++) {
            float x1 = UNIT_X[i] * radius;
            float z1 = UNIT_Z[i] * radius;
            float x2 = UNIT_X[i + 1] * radius;
            float z2 = UNIT_Z[i + 1] * radius;

            // A degenerate quad keeps the glow in the same batch as the vertical rings.
            builder.vertex(matrix, 0.0F, 0.05F, 0.0F).color(red, green, blue, 0.0F).endVertex();
            builder.vertex(matrix, x1, 0.05F, z1).color(red, green, blue, alpha).endVertex();
            builder.vertex(matrix, x2, 0.05F, z2).color(red, green, blue, alpha).endVertex();
            builder.vertex(matrix, 0.0F, 0.05F, 0.0F).color(red, green, blue, 0.0F).endVertex();
        }
    }
}
