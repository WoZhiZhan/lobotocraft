package com.wzz.lobotocraft.debug.prototype.queenlaser;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QueenLaserPrototypeRenderer {
    private static final int TUBE_SEGMENTS = 12;
    private static final int AXIAL_SEGMENTS = 20;
    private static final int RING_SEGMENTS = 64;
    private static final ResourceLocation[] ORIGINAL_CASTING_CIRCLES = {
            ResourceUtil.createInstance("textures/effect/queen_laser/casting_circle_1.png"),
            ResourceUtil.createInstance("textures/effect/queen_laser/casting_circle_2.png"),
            ResourceUtil.createInstance("textures/effect/queen_laser/casting_circle_3.png"),
            ResourceUtil.createInstance("textures/effect/queen_laser/casting_circle_4.png")
    };

    private QueenLaserPrototypeRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.level != null && !minecraft.isPaused()) {
            QueenLaserPrototypeManager.tick();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            QueenLaserPrototypeManager.clear();
            QueenLaserPrototypePostEffect.release();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        float exposure = QueenLaserPrototypeManager.getExposure();
        if (exposure <= 0.001F) return;
        QueenLaserPrototypePostEffect.render(event.getGuiGraphics(), exposure);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        QueenLaserPrototypeManager.Beam beam = QueenLaserPrototypeManager.getActive();
        if (beam == null || !beam.isMax()) return;
        float fireProgress = beam.fireProgress((float) event.getPartialTick());
        float firing = QueenLaserTimeline.firingAlpha(fireProgress);
        if (firing <= 0.0F) return;

        Vec3 camera = event.getCamera().getPosition();
        double distance = distanceToSegment(camera, beam.start, beam.end);
        float proximity = 1.0F - Mth.clamp((float) distance / 42.0F, 0.0F, 1.0F);
        float strength = firing * proximity * proximity;
        double time = beam.ageTicks + event.getPartialTick();
        event.setYaw(event.getYaw() + (float) Math.sin(time * 1.73D) * 0.38F * strength);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 2.31D + 1.2D) * 0.30F * strength);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 1.11D + 2.4D) * 0.52F * strength);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        QueenLaserPrototypeManager.Beam beam = QueenLaserPrototypeManager.getActive();
        if (beam == null) return;
        render(event, beam);
    }

    private static void render(RenderLevelStageEvent event, QueenLaserPrototypeManager.Beam beam) {
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 start = beam.start.subtract(cameraPos);
        Vec3 fullEnd = beam.end.subtract(cameraPos);
        Vec3 axis = fullEnd.subtract(start);
        double length = axis.length();
        if (length < 0.01D) return;

        Vec3 direction = axis.scale(1.0D / length);
        QueenLaserPrototypeManager.Basis basis = QueenLaserPrototypeManager.basis(direction);
        float castProgress = beam.castProgress(event.getPartialTick());
        float fireProgress = beam.fireProgress(event.getPartialTick());
        float time = beam.ageTicks + event.getPartialTick();
        float beamGrowth = easeOutCubic(Mth.clamp(fireProgress / 0.08F, 0.0F, 1.0F));
        Vec3 end = start.lerp(fullEnd, beamGrowth);
        float fade = QueenLaserTimeline.endEnvelope(fireProgress);
        float ignition = smoothstep(0.0F, 0.08F, fireProgress);
        float beamAlpha = ignition * fade;
        float secondStage = QueenLaserTimeline.secondStage(fireProgress);
        float pulse = 1.0F + (float) Math.sin(time * 0.78F) * 0.055F
                + (float) Math.sin(time * 2.41F) * 0.018F;
        float width = (beam.isMax() ? 2.65F : 1.55F) * pulse * Mth.lerp(secondStage, 0.82F, 1.18F);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        try {
            renderOriginalCastingCircles(poseStack.last().pose(), start, direction, basis,
                    time, castProgress, fireProgress, beam.isMax());

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = poseStack.last().pose();

            renderMagicSeal(builder, matrix, start, direction, basis, time,
                    castProgress, fireProgress, beam.isMax(), beamAlpha);
            if (beamAlpha > 0.001F && beamGrowth > 0.01F) {
                renderBeam(builder, matrix, start, end, direction, basis, width, time,
                        secondStage, beamAlpha, beam.isMax());
                renderFlowingShards(builder, matrix, start, end, direction, basis, width,
                        time, beamAlpha, beam.isMax());
                renderImpact(builder, matrix, end, direction, basis, width, time,
                        beamAlpha, secondStage, beam.isMax());
            }
            tesselator.end();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void renderBeam(BufferBuilder builder, Matrix4f matrix, Vec3 start, Vec3 end,
                                   Vec3 direction, QueenLaserPrototypeManager.Basis basis,
                                   float width, float time, float secondStage, float alpha,
                                   boolean maximum) {
        Color outerStart = mix(new Color(0.20F, 0.05F, 1.0F, alpha * 0.16F),
                new Color(0.08F, 0.72F, 1.0F, alpha * 0.22F), secondStage);
        Color outerEnd = mix(new Color(1.0F, 0.02F, 0.62F, alpha * 0.22F),
                new Color(0.78F, 0.08F, 1.0F, alpha * 0.28F), secondStage);
        drawTube(builder, matrix, start, end, basis, width * 1.38F,
                outerStart, outerEnd, time * 0.025F, maximum ? TUBE_SEGMENTS : 8);
        drawTube(builder, matrix, start, end, basis, width,
                mix(new Color(0.46F, 0.08F, 1.0F, alpha * 0.42F),
                        new Color(0.08F, 0.82F, 1.0F, alpha * 0.48F), secondStage),
                new Color(1.0F, 0.08F, 0.76F, alpha * 0.52F),
                -time * 0.04F, TUBE_SEGMENTS);
        drawTube(builder, matrix, start, end, basis, width * 0.56F,
                new Color(1.0F, 0.42F, 0.92F, alpha * 0.76F),
                mix(new Color(1.0F, 0.18F, 0.76F, alpha * 0.82F),
                        new Color(0.62F, 0.94F, 1.0F, alpha * 0.90F), secondStage),
                time * 0.06F, TUBE_SEGMENTS);
        drawTube(builder, matrix, start, end, basis, width * 0.20F,
                new Color(1.0F, 1.0F, 1.0F, alpha),
                new Color(1.0F, 0.88F, 1.0F, alpha),
                0.0F, 8);

        int ribbonCount = maximum ? 4 : 2;
        for (int i = 0; i < ribbonCount; i++) {
            drawRibbon(builder, matrix, start, end, direction, basis,
                    width * (1.05F + i * 0.10F), width * 0.075F,
                    time * (0.075F + i * 0.008F) + i * (float) Math.PI / ribbonCount,
                    maximum ? 2.5F : 1.5F,
                    new Color(i % 2 == 0 ? 0.34F : 1.0F, 0.35F,
                            i % 2 == 0 ? 1.0F : 0.82F, alpha * 0.52F));
        }
    }

    private static void renderOriginalCastingCircles(Matrix4f matrix, Vec3 start, Vec3 direction,
                                                     QueenLaserPrototypeManager.Basis basis,
                                                     float time, float castProgress,
                                                     float fireProgress, boolean maximum) {
        float charge = QueenLaserTimeline.mainCircleAlpha(castProgress, fireProgress);
        if (charge <= 0.001F) return;

        float base = maximum ? 3.65F : 2.45F;
        Vec3 center = start.subtract(direction.scale(0.20D));
        float[] scales = {0.92F, 1.18F, 0.82F, 0.58F};
        float[] speeds = {0.314F, -0.314F, 0.628F, 0.0F};
        float[] alpha = {0.78F, 0.92F, 0.72F, 0.88F};
        float[] aspect = {1.0F, 1.0F, 393.0F / 377.0F, 252.0F / 242.0F};

        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        for (int i = 0; i < ORIGINAL_CASTING_CIRCLES.length; i++) {
            float layerScale = QueenLaserTimeline.mainCircleLayerScale(castProgress, i);
            if (layerScale <= 0.001F) continue;
            double wobbleAngle = time * (0.055D + i * 0.012D) + i * 1.7D;
            Vec3 layerCenter = center
                    .add(basis.side().scale(Math.sin(wobbleAngle) * base * 0.035F))
                    .add(basis.up().scale(Math.cos(wobbleAngle * 0.83D) * base * 0.025F));
            RenderSystem.setShaderTexture(0, ORIGINAL_CASTING_CIRCLES[i]);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            drawTexturedQuad(builder, matrix, layerCenter, basis.side(), basis.up(),
                    base * scales[i] * layerScale, base * scales[i] * aspect[i] * layerScale,
                    time * speeds[i] + i * 0.37F,
                    new Color(1.0F, 1.0F, 1.0F, charge * alpha[i]));
            tesselator.end();
        }
    }

    private static void renderMagicSeal(BufferBuilder builder, Matrix4f matrix, Vec3 start,
                                        Vec3 direction, QueenLaserPrototypeManager.Basis basis,
                                        float time, float castProgress, float fireProgress,
                                        boolean maximum, float beamAlpha) {
        float glitter = QueenLaserTimeline.glitterAlpha(castProgress, fireProgress);
        float razor = QueenLaserTimeline.razorAlpha(castProgress, fireProgress);
        float main = QueenLaserTimeline.mainCircleAlpha(castProgress, fireProgress);
        float colored = QueenLaserTimeline.coloredRingAlpha(castProgress, fireProgress);
        float pillar = QueenLaserTimeline.pillarAlpha(castProgress, fireProgress);
        float charge = Math.max(Math.max(glitter, razor), Math.max(main, Math.max(colored, pillar)));
        if (charge <= 0.001F) return;
        float scaleIn = easeOutCubic(Mth.clamp(castProgress / 0.10F, 0.0F, 1.0F));
        Vec3 center = start.subtract(direction.scale(0.18D));
        float base = (maximum ? 3.6F : 2.35F) * scaleIn;

        drawDisc(builder, matrix, center, basis.side(), basis.up(), base * 0.84F,
                new Color(0.48F, 0.04F, 0.92F, main * 0.055F));
        drawRing(builder, matrix, center, basis.side(), basis.up(), base, base * 0.065F,
                time * 0.018F, new Color(1.0F, 0.18F, 0.82F, razor * 0.72F));
        drawRing(builder, matrix, center, basis.side(), basis.up(), base * 0.72F, base * 0.035F,
                -time * 0.032F, new Color(0.45F, 0.72F, 1.0F, colored * 0.78F));
        drawRing(builder, matrix, center, basis.side(), basis.up(), base * 0.42F, base * 0.05F,
                time * 0.055F, new Color(1.0F, 0.88F, 1.0F, main * 0.94F));

        int spokes = maximum ? 12 : 8;
        for (int i = 0; i < spokes; i++) {
            double angle = Math.PI * 2.0D * i / spokes + time * (maximum ? -0.018D : 0.012D);
            Vec3 radial = basis.side().scale(Math.cos(angle)).add(basis.up().scale(Math.sin(angle)));
            Vec3 tangent = basis.side().scale(-Math.sin(angle)).add(basis.up().scale(Math.cos(angle)));
            Vec3 inner = center.add(radial.scale(base * 0.46F));
            Vec3 outer = center.add(radial.scale(base * (i % 2 == 0 ? 0.92F : 0.78F)));
            drawSegment(builder, matrix, inner, outer, tangent.scale(base * 0.018F),
                    new Color(i % 2 == 0 ? 1.0F : 0.42F, 0.28F,
                            i % 2 == 0 ? 0.86F : 1.0F, main * 0.62F));
            drawDiamond(builder, matrix, outer, tangent.scale(base * 0.08F),
                    radial.scale(base * 0.12F), new Color(1.0F, 0.74F, 1.0F, glitter * 0.72F));
        }

        float orbPulse = 0.78F + (float) Math.sin(time * 0.38F) * 0.12F;
        drawDisc(builder, matrix, start, basis.side(), basis.up(),
                (maximum ? 1.35F : 0.9F) * orbPulse,
                new Color(1.0F, 0.72F, 1.0F,
                        Math.max(colored * 0.30F, pillar * 0.68F) + beamAlpha * 0.16F));
    }

    private static void renderFlowingShards(BufferBuilder builder, Matrix4f matrix,
                                            Vec3 start, Vec3 end, Vec3 direction,
                                            QueenLaserPrototypeManager.Basis basis, float width,
                                            float time, float alpha, boolean maximum) {
        int count = maximum ? 32 : 14;
        Vec3 axis = end.subtract(start);
        for (int i = 0; i < count; i++) {
            float lane = i / (float) count;
            float along = fract(lane + time * (0.006F + (i % 5) * 0.0007F));
            double angle = i * 2.399963D + time * (0.024D + (i % 3) * 0.004D);
            float orbit = width * (0.72F + (i % 4) * 0.13F);
            Vec3 radial = basis.side().scale(Math.cos(angle) * orbit)
                    .add(basis.up().scale(Math.sin(angle) * orbit));
            Vec3 center = start.add(axis.scale(along)).add(radial);
            Vec3 tangent = direction.cross(radial).normalize();
            float size = width * (0.045F + (i % 3) * 0.014F);
            drawDiamond(builder, matrix, center, tangent.scale(size),
                    direction.scale(size * 1.9F),
                    new Color(i % 2 == 0 ? 0.45F : 1.0F, 0.62F,
                            i % 2 == 0 ? 1.0F : 0.88F, alpha * 0.82F));
        }
    }

    private static void renderImpact(BufferBuilder builder, Matrix4f matrix, Vec3 end,
                                     Vec3 direction, QueenLaserPrototypeManager.Basis basis,
                                     float width, float time, float alpha, float secondStage,
                                     boolean maximum) {
        float core = width * (maximum ? 1.65F : 1.25F)
                * (0.92F + (float) Math.sin(time * 0.64F) * 0.08F);
        drawDisc(builder, matrix, end.add(direction.scale(0.03D)), basis.side(), basis.up(),
                core, new Color(1.0F, 0.52F + secondStage * 0.35F, 1.0F, alpha * 0.52F));
        drawRing(builder, matrix, end, basis.side(), basis.up(), core * 1.12F, core * 0.12F,
                -time * 0.04F, new Color(1.0F, 0.16F, 0.82F, alpha * 0.82F));

        int waves = maximum ? 4 : 2;
        for (int i = 0; i < waves; i++) {
            float wave = fract(time * (maximum ? 0.035F : 0.026F) + i / (float) waves);
            float radius = core * (1.0F + wave * (maximum ? 5.2F : 3.4F));
            float waveAlpha = alpha * (1.0F - wave) * (1.0F - wave) * 0.66F;
            drawRing(builder, matrix, end, basis.side(), basis.up(), radius,
                    Math.max(0.035F, width * 0.055F * (1.0F - wave)),
                    time * 0.025F, new Color(i % 2 == 0 ? 1.0F : 0.35F, 0.28F,
                            1.0F, waveAlpha));
        }
    }

    private static void drawTube(BufferBuilder builder, Matrix4f matrix, Vec3 start, Vec3 end,
                                 QueenLaserPrototypeManager.Basis basis, float radius,
                                 Color startColor, Color endColor, float rotation, int radialSegments) {
        Vec3 axis = end.subtract(start);
        for (int axial = 0; axial < AXIAL_SEGMENTS; axial++) {
            float t0 = axial / (float) AXIAL_SEGMENTS;
            float t1 = (axial + 1) / (float) AXIAL_SEGMENTS;
            float r0 = radius * edgeTaper(t0);
            float r1 = radius * edgeTaper(t1);
            Vec3 c0 = start.add(axis.scale(t0));
            Vec3 c1 = start.add(axis.scale(t1));
            Color color0 = mix(startColor, endColor, t0);
            Color color1 = mix(startColor, endColor, t1);
            for (int radial = 0; radial < radialSegments; radial++) {
                double a0 = Math.PI * 2.0D * radial / radialSegments + rotation;
                double a1 = Math.PI * 2.0D * (radial + 1) / radialSegments + rotation;
                Vec3 radial0 = basis.side().scale(Math.cos(a0)).add(basis.up().scale(Math.sin(a0)));
                Vec3 radial1 = basis.side().scale(Math.cos(a1)).add(basis.up().scale(Math.sin(a1)));
                vertex(builder, matrix, c0.add(radial0.scale(r0)), color0);
                vertex(builder, matrix, c0.add(radial1.scale(r0)), color0);
                vertex(builder, matrix, c1.add(radial1.scale(r1)), color1);
                vertex(builder, matrix, c1.add(radial0.scale(r1)), color1);
            }
        }
    }

    private static void drawRibbon(BufferBuilder builder, Matrix4f matrix, Vec3 start, Vec3 end,
                                   Vec3 direction, QueenLaserPrototypeManager.Basis basis,
                                   float radius, float halfWidth, float rotation, float turns,
                                   Color color) {
        Vec3 axis = end.subtract(start);
        for (int i = 0; i < AXIAL_SEGMENTS; i++) {
            float t0 = i / (float) AXIAL_SEGMENTS;
            float t1 = (i + 1) / (float) AXIAL_SEGMENTS;
            double a0 = rotation + t0 * Math.PI * 2.0D * turns;
            double a1 = rotation + t1 * Math.PI * 2.0D * turns;
            Vec3 radial0 = basis.side().scale(Math.cos(a0)).add(basis.up().scale(Math.sin(a0)));
            Vec3 radial1 = basis.side().scale(Math.cos(a1)).add(basis.up().scale(Math.sin(a1)));
            Vec3 p0 = start.add(axis.scale(t0)).add(radial0.scale(radius * edgeTaper(t0)));
            Vec3 p1 = start.add(axis.scale(t1)).add(radial1.scale(radius * edgeTaper(t1)));
            Vec3 w0 = direction.cross(radial0).normalize().scale(halfWidth);
            Vec3 w1 = direction.cross(radial1).normalize().scale(halfWidth);
            vertex(builder, matrix, p0.subtract(w0), color);
            vertex(builder, matrix, p0.add(w0), color);
            vertex(builder, matrix, p1.add(w1), color);
            vertex(builder, matrix, p1.subtract(w1), color);
        }
    }

    private static void drawRing(BufferBuilder builder, Matrix4f matrix, Vec3 center,
                                 Vec3 axisA, Vec3 axisB, float radius, float thickness,
                                 float rotation, Color color) {
        float inner = Math.max(0.0F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / RING_SEGMENTS + rotation;
            double a1 = Math.PI * 2.0D * (i + 1) / RING_SEGMENTS + rotation;
            Vec3 r0 = axisA.scale(Math.cos(a0)).add(axisB.scale(Math.sin(a0)));
            Vec3 r1 = axisA.scale(Math.cos(a1)).add(axisB.scale(Math.sin(a1)));
            vertex(builder, matrix, center.add(r0.scale(inner)), color);
            vertex(builder, matrix, center.add(r0.scale(outer)), color);
            vertex(builder, matrix, center.add(r1.scale(outer)), color);
            vertex(builder, matrix, center.add(r1.scale(inner)), color);
        }
    }

    private static void drawDisc(BufferBuilder builder, Matrix4f matrix, Vec3 center,
                                 Vec3 axisA, Vec3 axisB, float radius, Color color) {
        Color edge = color.withAlpha(0.0F);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / RING_SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / RING_SEGMENTS;
            Vec3 p0 = center.add(axisA.scale(Math.cos(a0) * radius))
                    .add(axisB.scale(Math.sin(a0) * radius));
            Vec3 p1 = center.add(axisA.scale(Math.cos(a1) * radius))
                    .add(axisB.scale(Math.sin(a1) * radius));
            vertex(builder, matrix, center, color);
            vertex(builder, matrix, p0, edge);
            vertex(builder, matrix, p1, edge);
            vertex(builder, matrix, center, color);
        }
    }

    private static void drawSegment(BufferBuilder builder, Matrix4f matrix, Vec3 start, Vec3 end,
                                    Vec3 width, Color color) {
        vertex(builder, matrix, start.subtract(width), color);
        vertex(builder, matrix, start.add(width), color);
        vertex(builder, matrix, end.add(width), color);
        vertex(builder, matrix, end.subtract(width), color);
    }

    private static void drawDiamond(BufferBuilder builder, Matrix4f matrix, Vec3 center,
                                    Vec3 axisA, Vec3 axisB, Color color) {
        vertex(builder, matrix, center.add(axisA), color);
        vertex(builder, matrix, center.add(axisB), color.withAlpha(color.alpha * 0.65F));
        vertex(builder, matrix, center.subtract(axisA), color);
        vertex(builder, matrix, center.subtract(axisB), color.withAlpha(color.alpha * 0.65F));
    }

    private static void drawTexturedQuad(BufferBuilder builder, Matrix4f matrix, Vec3 center,
                                         Vec3 axisA, Vec3 axisB, float halfWidth, float halfHeight,
                                         float rotation, Color color) {
        double cosine = Math.cos(rotation);
        double sine = Math.sin(rotation);
        Vec3 rotatedA = axisA.scale(cosine).add(axisB.scale(sine));
        Vec3 rotatedB = axisA.scale(-sine).add(axisB.scale(cosine));
        Vec3 width = rotatedA.scale(halfWidth);
        Vec3 height = rotatedB.scale(halfHeight);

        vertex(builder, matrix, center.subtract(width).subtract(height), color, 0.0F, 1.0F);
        vertex(builder, matrix, center.add(width).subtract(height), color, 1.0F, 1.0F);
        vertex(builder, matrix, center.add(width).add(height), color, 1.0F, 0.0F);
        vertex(builder, matrix, center.subtract(width).add(height), color, 0.0F, 0.0F);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 position, Color color) {
        builder.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(color.red, color.green, color.blue, color.alpha).endVertex();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 position,
                               Color color, float u, float v) {
        builder.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(color.red, color.green, color.blue, color.alpha).uv(u, v).endVertex();
    }

    private static float edgeTaper(float t) {
        return 0.28F + 0.72F * (float) Math.pow(Math.sin(Math.PI * t), 0.22D);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static Color mix(Color first, Color second, float amount) {
        return new Color(Mth.lerp(amount, first.red, second.red),
                Mth.lerp(amount, first.green, second.green),
                Mth.lerp(amount, first.blue, second.blue),
                Mth.lerp(amount, first.alpha, second.alpha));
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 axis = end.subtract(start);
        double lengthSquared = axis.lengthSqr();
        if (lengthSquared < 1.0E-6D) return point.distanceTo(start);
        double t = Mth.clamp(point.subtract(start).dot(axis) / lengthSquared, 0.0D, 1.0D);
        return point.distanceTo(start.add(axis.scale(t)));
    }

    private record Color(float red, float green, float blue, float alpha) {
        Color withAlpha(float value) {
            return new Color(red, green, blue, value);
        }
    }
}
