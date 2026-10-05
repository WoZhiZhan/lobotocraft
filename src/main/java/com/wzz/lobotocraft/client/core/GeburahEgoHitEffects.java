package com.wzz.lobotocraft.client.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Tiny world-space contact accents. Never adds camera, screen, entity, or damage effects. */
@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeburahEgoHitEffects {
    private static final List<Hit> ACTIVE = new ArrayList<>();
    private static final int LIFETIME = 7;
    private static ClientLevel owner;

    private GeburahEgoHitEffects() {
    }

    public static void add(ResourceLocation dimension, GeburahMove move, Vec3 forward,
                           List<Vec3> contacts, int seed) {
        Minecraft mc = Minecraft.getInstance();
        tick();
        if (!enabled() || mc.level == null
                || !mc.level.dimension().location().equals(dimension)) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Vec3 contact : contacts) {
            if (ACTIVE.size() >= GeburahEgoAttackVisuals.MAX_ACTIVE_HITS) break;
            int segments = GeburahEgoAttackVisuals.hitSegments(
                    mc.options.particles().get().getId(), camera.distanceToSqr(contact));
            if (segments == 0) continue;
            ACTIVE.add(new Hit(move, contact, forward.normalize(),
                    mc.level.getGameTime(), seed, segments));
        }
    }

    private static boolean enabled() {
        return ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.get()
                && GeburahEffectCompositor.mode() != GeburahEffectCompositor.Mode.OFF;
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (owner != level) {
            clear();
            owner = level;
        }
        if (level == null || !enabled()) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.removeIf(hit -> level.getGameTime() - hit.start >= LIFETIME);
    }

    public static void clear() {
        ACTIVE.clear();
        owner = null;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || owner != mc.level || ACTIVE.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        double now = mc.level.getGameTime() + event.getPartialTick();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var tess = Tesselator.getInstance();
            var buffer = tess.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Hit hit : ACTIVE) {
                if (camera.distanceToSqr(hit.position) > 48 * 48) continue;
                float p = (float) ((now - hit.start) / LIFETIME);
                if (p < 0 || p >= 1) continue;
                float alpha = (1.0F - p) * (1.0F - p)
                        * (GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.FULL ? 1 : 0.65F);
                Vec3 side = new Vec3(-hit.forward.z, 0, hit.forward.x);
                Vec3 up = new Vec3(0, 1, 0);
                if (hit.move == GeburahMove.STAB) {
                    // Ring is perpendicular to the real attack direction, not a full-screen flash.
                    Vec3 center = hit.position.add(hit.forward.scale(p * 0.3D));
                    double radius = 0.12D + Math.sin(p * Math.PI * 0.5D) * 0.38D;
                    for (int i = 0; i < hit.segments; i++) {
                        double a = i * Math.PI * 2 / hit.segments;
                        double b = (i + 1) * Math.PI * 2 / hit.segments;
                        quad(buffer, matrix, camera,
                                ring(center, side, up, a, radius),
                                ring(center, side, up, b, radius),
                                ring(center, side, up, b, radius + 0.035D),
                                ring(center, side, up, a, radius + 0.035D), 1, 0.24F, 0.16F, alpha);
                    }
                    int streaks = hit.segments >= 8 ? 4 : 2;
                    for (int i = 0; i < streaks; i++) {
                        double a = i * Math.PI * 2 / streaks + (hit.seed & 7) * 0.1D;
                        Vec3 start = ring(center, side, up, a, 0.12D + p * 0.15D);
                        strip(buffer, matrix, camera, start,
                                start.add(hit.forward.scale(0.18D + p * 0.38D)),
                                side, 0.02D, 1, 0.48F, 0.35F, alpha * 0.75F);
                    }
                } else {
                    Vec3 axis = side.add(up.scale(0.22D)).normalize();
                    Vec3 normal = up.subtract(side.scale(0.22D)).normalize();
                    double length = 0.3D + p * 0.4D;
                    strip(buffer, matrix, camera, hit.position.subtract(axis.scale(length)),
                            hit.position.add(axis.scale(length)), normal,
                            0.085D * (1 - p), 0.4F, 0.005F, 0.015F, alpha);
                    strip(buffer, matrix, camera, hit.position.subtract(axis.scale(length * 0.8D)),
                            hit.position.add(axis.scale(length * 0.8D)), normal,
                            0.02D * (1 - p), 1, 0.35F, 0.25F, alpha);
                }
            }
            tess.end();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static Vec3 ring(Vec3 center, Vec3 side, Vec3 up, double a, double radius) {
        return center.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
    }

    private static void strip(BufferBuilder buf, Matrix4f m, Vec3 camera, Vec3 start, Vec3 end,
                              Vec3 side, double width, float r, float g, float b, float alpha) {
        Vec3 half = side.scale(width);
        quad(buf, m, camera, start.subtract(half), end.subtract(half),
                end.add(half), start.add(half), r, g, b, alpha);
    }

    private static void quad(BufferBuilder buf, Matrix4f m, Vec3 camera,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float blue, float alpha) {
        vertex(buf, m, a.subtract(camera), r, g, blue, alpha);
        vertex(buf, m, b.subtract(camera), r, g, blue, alpha);
        vertex(buf, m, c.subtract(camera), r, g, blue, alpha);
        vertex(buf, m, d.subtract(camera), r, g, blue, alpha);
    }

    private static void vertex(BufferBuilder buf, Matrix4f m, Vec3 p,
                               float r, float g, float b, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, alpha).endVertex();
    }

    private record Hit(GeburahMove move, Vec3 position, Vec3 forward, long start,
                       int seed, int segments) {
    }
}
