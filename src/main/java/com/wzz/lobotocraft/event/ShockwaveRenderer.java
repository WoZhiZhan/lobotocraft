package com.wzz.lobotocraft.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ShockwaveEffectManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ShockwaveRenderer {

    private static final int SEGMENTS = 64; // 圆环细分段数

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ShockwaveEffectManager.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<ShockwaveEffectManager.Shockwave> shockwaves = ShockwaveEffectManager.getShockwaves();
        if (shockwaves.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        for (ShockwaveEffectManager.Shockwave wave : shockwaves) {
            wave.tick(partialTick);

            // 相对相机的偏移
            double dx = wave.x - camPos.x;
            double dy = wave.y - camPos.y;
            double dz = wave.z - camPos.z;

            // 颜色解析
            float r = ((wave.color >> 16) & 0xFF) / 255f;
            float g = ((wave.color >> 8)  & 0xFF) / 255f;
            float b = (wave.color & 0xFF) / 255f;

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            Matrix4f matrix = poseStack.last().pose();

            // 渲染多层圆环
            for (int ring = 0; ring < wave.ringCount; ring++) {
                float radius = wave.currentRadius - ring * wave.ringSpacing;
                if (radius <= 0) continue;

                // 外圈透明度略低
                float ringAlpha = wave.alpha * (1f - ring * 0.25f);

                // 每层圆环由两个高度组成，形成"带状"
                float heightTop = 0.6f - ring * 0.15f;
                float heightBot = -0.3f + ring * 0.1f;

                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

                for (int i = 0; i < SEGMENTS; i++) {
                    float angle1 = (float)(i     / (double)SEGMENTS * Math.PI * 2);
                    float angle2 = (float)((i+1) / (double)SEGMENTS * Math.PI * 2);

                    float x1 = (float)Math.cos(angle1) * radius;
                    float z1 = (float)Math.sin(angle1) * radius;
                    float x2 = (float)Math.cos(angle2) * radius;
                    float z2 = (float)Math.sin(angle2) * radius;

                    // 上沿透明，下沿不透明 → 产生渐变感
                    builder.vertex(matrix, x1, heightTop, z1).color(r, g, b, 0f       ).endVertex();
                    builder.vertex(matrix, x2, heightTop, z2).color(r, g, b, 0f       ).endVertex();
                    builder.vertex(matrix, x2, heightBot, z2).color(r, g, b, ringAlpha).endVertex();
                    builder.vertex(matrix, x1, heightBot, z1).color(r, g, b, ringAlpha).endVertex();
                }

                tesselator.end();
            }

            // 额外：地面光晕（扁平圆盘）
            renderGroundGlow(matrix, builder, tesselator,
                wave.currentRadius, r, g, b, wave.alpha * 0.3f);

            poseStack.popPose();
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /** 地面半透明光晕 */
    private static void renderGroundGlow(Matrix4f matrix, BufferBuilder builder,
                                          Tesselator tesselator,
                                          float radius, float r, float g, float b, float alpha) {
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // 中心点透明
        builder.vertex(matrix, 0, 0.05f, 0).color(r, g, b, 0f).endVertex();

        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (float)(i / (double)SEGMENTS * Math.PI * 2);
            float x = (float)Math.cos(angle) * radius;
            float z = (float)Math.sin(angle) * radius;
            builder.vertex(matrix, x, 0.05f, z).color(r, g, b, alpha).endVertex();
        }

        tesselator.end();
    }
}