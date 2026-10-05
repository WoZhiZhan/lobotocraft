package com.wzz.lobotocraft.client.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeburahBladeTrailRenderer {
    private static final ResourceLocation FLESH_TEXTURE =
            ResourceUtil.createInstance("textures/effect/geburah_blade_flesh.png");
    private static boolean debugMarkers;
    private static boolean trailSurfaceEnabled = true;

    private GeburahBladeTrailRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        double now = minecraft.level.getGameTime() + event.getPartialTick();
        List<GeburahBladeTrailState.TrailPoint> points =
                GeburahBladeTrailState.visualTrail(now, ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.get());
        if (points.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        try {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            if (trailSurfaceEnabled) {
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int i = 1; i < points.size(); i++) {
                    drawBaseSegment(builder, matrix, camera,
                            points.get(i - 1), points.get(i), i);
                }
                tesselator.end();

                RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
                RenderSystem.setShaderTexture(0, FLESH_TEXTURE);
                builder.begin(VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_COLOR_TEX);
                for (int i = 1; i < points.size(); i++) {
                    drawFleshSegment(builder, matrix, camera,
                            points.get(i - 1), points.get(i));
                }
                tesselator.end();

                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int i = 1; i < points.size(); i++) {
                    drawHighlightSegment(builder, matrix, camera,
                            points.get(i - 1), points.get(i), i);
                }
                tesselator.end();
            }

            if (debugMarkers) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                drawDebugMarkers(builder, matrix, camera, points.get(points.size() - 1));
                tesselator.end();
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    public static void setDebugMarkers(boolean enabled) {
        debugMarkers = enabled;
    }

    public static boolean debugMarkers() {
        return debugMarkers;
    }

    public static void setTrailSurfaceEnabled(boolean enabled) {
        trailSurfaceEnabled = enabled;
    }

    public static boolean trailSurfaceEnabled() {
        return trailSurfaceEnabled;
    }

    private static void drawDebugMarkers(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                         GeburahBladeTrailState.TrailPoint point) {
        List<Vec3> positions = point.bladePositions();
        for (int i = 0; i < positions.size(); i++) {
            float progress = positions.size() == 1 ? 1.0F : i / (float) (positions.size() - 1);
            float red = progress;
            float green = 1.0F - Math.abs(progress - 0.5F) * 1.2F;
            float blue = 1.0F - progress;
            drawDebugMarker(builder, matrix, camera, positions.get(i), red, green, blue);
        }
    }

    private static void drawDebugMarker(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                        Vec3 center, float red, float green, float blue) {
        double radius = 0.075D;
        drawPatch(builder, matrix, camera, center,
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
                radius, radius, red, green, blue, 0.95F);
        drawPatch(builder, matrix, camera, center,
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                radius, radius, red, green, blue, 0.95F);
        drawPatch(builder, matrix, camera, center,
                new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                radius, radius, red, green, blue, 0.95F);
    }

    private static void drawFleshSegment(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                         GeburahBladeTrailState.TrailPoint previous,
                                         GeburahBladeTrailState.TrailPoint current) {
        float previousAlpha = trailFade(previous) * (current.ego() ? 0.94F : 0.78F);
        float currentAlpha = trailFade(current) * (current.ego() ? 1.0F : 0.86F);
        float previousTrailCoordinate = trailCoordinate(previous.age());
        float currentTrailCoordinate = trailCoordinate(current.age());
        List<Vec3> previousBlade = previous.bladePositions();
        List<Vec3> currentBlade = current.bladePositions();

        if (previousBlade.size() >= 2 && previousBlade.size() == currentBlade.size()) {
            int bladeSegments = currentBlade.size() - 1;
            for (int point = 1; point < currentBlade.size(); point++) {
                float baseU = (point - 1) / (float) bladeSegments;
                float tipU = point / (float) bladeSegments;
                drawTexturedSweep(builder, matrix, camera,
                        previousBlade.get(point - 1), previousBlade.get(point),
                        currentBlade.get(point), currentBlade.get(point - 1),
                        baseU, tipU,
                        previousTrailCoordinate, currentTrailCoordinate,
                        previousAlpha, currentAlpha);
            }
            return;
        }

        Vec3 start = previous.position();
        Vec3 end = current.position();
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 1.0E-6D) return;
        Vec3 midpoint = start.lerp(end, 0.5D);
        Vec3 side = axis.cross(camera.subtract(midpoint));
        if (side.lengthSqr() < 1.0E-6D) side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        double width = enhanced(current) ? 0.15D : current.ego() ? 0.24D : 0.16D;
        Vec3 half = side.normalize().scale(width);
        drawTexturedQuad(builder, matrix, camera,
                start.subtract(half), end.subtract(half),
                end.add(half), start.add(half),
                previousTrailCoordinate, currentTrailCoordinate, 0.0F, 1.0F,
                previousAlpha, currentAlpha);
    }

    private static void drawBaseSegment(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                        GeburahBladeTrailState.TrailPoint previous,
                                        GeburahBladeTrailState.TrailPoint current, int index) {
        TrailSegment segment = createSegment(camera, previous, current, index);
        if (segment == null) return;

        drawBladeSweepBase(builder, matrix, camera, previous, current,
                segment.startAlpha(), segment.endAlpha());
        drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                segment.width() * 1.28D, 0.0D,
                0.015F, 0.0F, 0.0F,
                segment.startAlpha() * 0.72F, segment.endAlpha() * 0.72F);
        drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                segment.width(), segment.width() * 0.08D,
                segment.ego() ? 0.48F : 0.32F, 0.005F, 0.008F,
                segment.startAlpha() * 0.52F, segment.endAlpha() * 0.52F);
        if (segment.move() != GeburahMove.STAB) {
            double splinterOffset = segment.width() * (0.78D + (index % 3) * 0.13D);
            drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                    segment.width() * 0.22D,
                    index % 2 == 0 ? splinterOffset : -splinterOffset,
                    0.20F, 0.0F, 0.006F,
                    segment.startAlpha() * 0.48F, segment.endAlpha() * 0.48F);
        }
        if (enhanced(current) && current.move() == GeburahMove.SLASH) {
            drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                    segment.width() * 0.14D, segment.width() * 1.15D,
                    0.24F, 0.005F, 0.015F,
                    segment.startAlpha() * 0.4F, segment.endAlpha() * 0.4F);
        }
    }

    private static void drawHighlightSegment(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                             GeburahBladeTrailState.TrailPoint previous,
                                             GeburahBladeTrailState.TrailPoint current, int index) {
        TrailSegment segment = createSegment(camera, previous, current, index);
        if (segment == null) return;

        drawBladeSweepHighlight(builder, matrix, camera, previous, current,
                segment.startAlpha(), segment.endAlpha());
        drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                segment.width() * 0.52D, -segment.width() * 0.12D,
                0.96F, 0.018F, 0.022F,
                segment.startAlpha() * (segment.ego() ? 0.76F : 0.54F),
                segment.endAlpha() * (segment.ego() ? 0.76F : 0.54F));
        drawRibbon(builder, matrix, camera, segment.start(), segment.end(), segment.side(),
                segment.width() * 0.13D, segment.width() * 0.18D,
                1.0F, 0.56F, 0.52F,
                segment.startAlpha() * 0.78F, segment.endAlpha() * 0.78F);
        boolean showEyeGlint = segment.age() < 0.82F
                && (segment.ego() ? index % 2 == 0 : index % 4 == 0);
        if (showEyeGlint) {
            drawEyeGlint(builder, matrix, camera,
                    segment.start(), segment.end(), segment.side(),
                    segment.move(), index,
                    Math.min(segment.startAlpha(), segment.endAlpha())
                            * (segment.ego() ? 1.0F : 0.58F));
        }
        if (enhanced(current) && current.move() == GeburahMove.STAB) {
            Vec3 direction = segment.end().subtract(segment.start()).normalize();
            double length = Math.min(0.7D, segment.start().distanceTo(segment.end()) * 1.4D);
            drawPatch(builder, matrix, camera, segment.end(), direction, segment.side(),
                    0.10D, 0.045D, 1.0F, 0.72F, 0.63F, segment.endAlpha());
            for (int streak = 0; streak < 2; streak++) {
                double offset = streak == 0 ? -0.22D : 0.22D;
                Vec3 tip = segment.end().add(segment.side().scale(offset));
                drawRibbon(builder, matrix, camera, tip.subtract(direction.scale(length)),
                        tip, segment.side(), 0.018D, 0,
                        0.85F, 0.05F, 0.06F, 0.0F, segment.endAlpha() * 0.5F);
            }
        }
    }

    private static TrailSegment createSegment(Vec3 camera,
                                              GeburahBladeTrailState.TrailPoint previous,
                                              GeburahBladeTrailState.TrailPoint current,
                                              int index) {
        Vec3 start = previous.position();
        Vec3 end = current.position();
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 1.0E-6D) return null;
        Vec3 midpoint = start.lerp(end, 0.5D);
        Vec3 side = axis.cross(camera.subtract(midpoint));
        if (side.lengthSqr() < 1.0E-6D) side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();

        float startAlpha = trailFade(previous);
        float endAlpha = trailFade(current);
        GeburahMove move = current.move() == null ? GeburahMove.SLASH : current.move();
        double moveWidth = switch (move) {
            case SLASH -> 0.42D;
            case STAB -> 0.19D;
            case VERTICAL_SLASH -> 0.50D;
        };
        double formScale = current.ego() ? 1.0D : 0.72D;
        double ragged = 0.88D + Math.sin(index * 2.43D + current.age() * 19.0D) * 0.12D;
        double width = moveWidth * formScale * ragged;
        if (enhanced(current)) width *= GeburahEgoAttackVisuals.widthScale(move, current.age());
        return new TrailSegment(start, end, side, width, startAlpha, endAlpha,
                current.age(), current.ego(), move);
    }

    private static void drawBladeSweepBase(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                           GeburahBladeTrailState.TrailPoint previous,
                                           GeburahBladeTrailState.TrailPoint current,
                                           float previousAlpha, float currentAlpha) {
        List<Vec3> previousBlade = previous.bladePositions();
        List<Vec3> currentBlade = current.bladePositions();
        if (previousBlade.size() < 2 || previousBlade.size() != currentBlade.size()) return;

        for (int point = 1; point < currentBlade.size(); point++) {
            Vec3 previousBase = previousBlade.get(point - 1);
            Vec3 previousTip = previousBlade.get(point);
            Vec3 currentBase = currentBlade.get(point - 1);
            Vec3 currentTip = currentBlade.get(point);
            drawSweep(builder, matrix, camera,
                    previousBase, previousTip, currentTip, currentBase,
                    0.015F, 0.0F, 0.0F,
                    previousAlpha * 0.22F, currentAlpha * 0.30F);
        }
    }

    private static void drawBladeSweepHighlight(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                                GeburahBladeTrailState.TrailPoint previous,
                                                GeburahBladeTrailState.TrailPoint current,
                                                float previousAlpha, float currentAlpha) {
        List<Vec3> previousBlade = previous.bladePositions();
        List<Vec3> currentBlade = current.bladePositions();
        if (previousBlade.size() < 2 || previousBlade.size() != currentBlade.size()) return;

        for (int point = 1; point < currentBlade.size(); point++) {
            Vec3 previousBase = previousBlade.get(point - 1);
            Vec3 previousTip = previousBlade.get(point);
            Vec3 currentBase = currentBlade.get(point - 1);
            Vec3 currentTip = currentBlade.get(point);
            drawSweep(builder, matrix, camera,
                    previousBase.lerp(previousTip, 0.28D), previousTip,
                    currentTip, currentBase.lerp(currentTip, 0.28D),
                    current.ego() ? 0.58F : 0.38F, 0.006F, 0.012F,
                    previousAlpha * 0.10F, currentAlpha * 0.16F);
        }
    }

    private static void drawTexturedSweep(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                          Vec3 previousBase, Vec3 previousTip,
                                          Vec3 currentTip, Vec3 currentBase,
                                          float baseU, float tipU,
                                          float previousV, float currentV,
                                          float previousAlpha, float currentAlpha) {
        texturedVertex(builder, matrix, previousBase.subtract(camera),
                baseU, previousV, previousAlpha);
        texturedVertex(builder, matrix, currentBase.subtract(camera),
                baseU, currentV, currentAlpha);
        texturedVertex(builder, matrix, currentTip.subtract(camera),
                tipU, currentV, currentAlpha);
        texturedVertex(builder, matrix, previousTip.subtract(camera),
                tipU, previousV, previousAlpha);
    }

    private static void drawTexturedQuad(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                         Vec3 previousBase, Vec3 currentBase,
                                         Vec3 currentTip, Vec3 previousTip,
                                         float previousU, float currentU,
                                         float baseV, float tipV,
                                         float previousAlpha, float currentAlpha) {
        texturedVertex(builder, matrix, previousBase.subtract(camera),
                previousU, baseV, previousAlpha);
        texturedVertex(builder, matrix, currentBase.subtract(camera),
                currentU, baseV, currentAlpha);
        texturedVertex(builder, matrix, currentTip.subtract(camera),
                currentU, tipV, currentAlpha);
        texturedVertex(builder, matrix, previousTip.subtract(camera),
                previousU, tipV, previousAlpha);
    }

    private static void drawEyeGlint(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                     Vec3 start, Vec3 end, Vec3 side, GeburahMove move,
                                     int index, float alpha) {
        Vec3 axis = end.subtract(start).normalize();
        double progress = 0.34D + (index % 3) * 0.17D;
        Vec3 center = start.lerp(end, progress).add(side.scale((index % 2 == 0 ? 1 : -1)
                * (move == GeburahMove.STAB ? 0.035D : 0.11D)));
        Vec3 towardCamera = camera.subtract(center);
        if (towardCamera.lengthSqr() > 1.0E-6D) {
            center = center.add(towardCamera.normalize().scale(0.004D));
        }
        double halfLength = move == GeburahMove.STAB ? 0.085D : 0.12D;
        double halfWidth = move == GeburahMove.VERTICAL_SLASH ? 0.050D : 0.038D;
        drawPatch(builder, matrix, camera, center, axis, side, halfLength, halfWidth,
                0.96F, 0.92F, 0.88F, alpha * 0.92F);
        drawPatch(builder, matrix, camera, center, axis, side, halfLength * 0.34D,
                halfWidth * 0.58D, 0.025F, 0.0F, 0.0F, alpha);
        drawPatch(builder, matrix, camera, center, axis, side, halfLength * 0.10D,
                halfWidth * 0.24D, 0.92F, 0.10F, 0.08F, alpha);
    }

    private static void drawPatch(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                  Vec3 center, Vec3 axis, Vec3 side,
                                  double halfLength, double halfWidth,
                                  float red, float green, float blue, float alpha) {
        Vec3 along = axis.scale(halfLength);
        Vec3 across = side.scale(halfWidth);
        Vec3 a = center.subtract(along).subtract(across).subtract(camera);
        Vec3 b = center.add(along).subtract(across).subtract(camera);
        Vec3 c = center.add(along).add(across).subtract(camera);
        Vec3 d = center.subtract(along).add(across).subtract(camera);
        vertex(builder, matrix, a, red, green, blue, alpha);
        vertex(builder, matrix, b, red, green, blue, alpha);
        vertex(builder, matrix, c, red, green, blue, alpha);
        vertex(builder, matrix, d, red, green, blue, alpha);
    }

    private static void drawRibbon(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                   Vec3 start, Vec3 end, Vec3 side,
                                   double halfWidth, double lateralOffset,
                                   float red, float green, float blue,
                                   float startAlpha, float endAlpha) {
        Vec3 centerOffset = side.scale(lateralOffset);
        Vec3 half = side.scale(halfWidth);
        Vec3 a = start.add(centerOffset).subtract(half).subtract(camera);
        Vec3 b = end.add(centerOffset).subtract(half).subtract(camera);
        Vec3 c = end.add(centerOffset).add(half).subtract(camera);
        Vec3 d = start.add(centerOffset).add(half).subtract(camera);
        vertex(builder, matrix, a, red, green, blue, startAlpha);
        vertex(builder, matrix, b, red, green, blue, endAlpha);
        vertex(builder, matrix, c, red, green, blue, endAlpha);
        vertex(builder, matrix, d, red, green, blue, startAlpha);
    }

    private static void drawSweep(BufferBuilder builder, Matrix4f matrix, Vec3 camera,
                                  Vec3 previousBase, Vec3 previousTip,
                                  Vec3 currentTip, Vec3 currentBase,
                                  float red, float green, float blue,
                                  float previousAlpha, float currentAlpha) {
        vertex(builder, matrix, previousBase.subtract(camera),
                red, green, blue, previousAlpha);
        vertex(builder, matrix, previousTip.subtract(camera),
                red, green, blue, previousAlpha);
        vertex(builder, matrix, currentTip.subtract(camera),
                red, green, blue, currentAlpha);
        vertex(builder, matrix, currentBase.subtract(camera),
                red, green, blue, currentAlpha);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 position,
                               float red, float green, float blue, float alpha) {
        builder.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(red, green, blue, alpha).endVertex();
    }

    private static void texturedVertex(BufferBuilder builder, Matrix4f matrix, Vec3 position,
                                       float u, float v, float alpha) {
        builder.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .endVertex();
    }

    private static float fade(float age) {
        float remaining = Math.max(0.0F, 1.0F - age);
        return remaining * remaining;
    }

    private static boolean enhanced(GeburahBladeTrailState.TrailPoint point) {
        return point.enhanced() && ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.get();
    }

    private static float trailFade(GeburahBladeTrailState.TrailPoint point) {
        return enhanced(point) ? GeburahEgoAttackVisuals.trailFade(point.age()) : fade(point.age());
    }

    private static float trailCoordinate(float age) {
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age));
    }

    private record TrailSegment(Vec3 start, Vec3 end, Vec3 side, double width,
                                float startAlpha, float endAlpha, float age,
                                boolean ego, GeburahMove move) {
    }
}
