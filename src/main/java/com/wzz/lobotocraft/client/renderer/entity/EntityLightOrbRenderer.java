package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.*;
import com.wzz.lobotocraft.entity.EntityLightOrb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class EntityLightOrbRenderer extends EntityRenderer<EntityLightOrb> {

    private static final RenderType ORB_RING;
    private static final RenderType ORB_SPHERE;

    static {
        ORB_RING    = RS.ring();
        ORB_SPHERE  = RS.sphere();
    }

    @SuppressWarnings("unused")
    private static final class RS extends RenderStateShard {
        private RS() { super("", () -> {}, () -> {}); }

        static RenderType ring() {
            return RenderType.create("light_orb_ring",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.TRIANGLE_STRIP, 512,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        static RenderType sphere() {
            return RenderType.create("light_orb_sphere",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.TRIANGLE_FAN, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }
    }

    private static final int    SPHERE_SEG   = 64;
    private static final int    RING_SEG     = 80;
    private static final float  SPHERE_R     = 0.22f;
    private static final float  RING_OUTER   = 0.26f;
    private static final float  RING_INNER   = 0.12f;

    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    private static final int TRAIL_MAX = 14;

    public EntityLightOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityLightOrb entity) { return null; }

    @Override
    public boolean shouldRender(EntityLightOrb entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(EntityLightOrb entity, float yaw, float pt,
                       PoseStack ps, MultiBufferSource buf, int light) {

        Vec3 curPos = new Vec3(
                Mth.lerp(pt, entity.xOld, entity.getX()),
                Mth.lerp(pt, entity.yOld, entity.getY()),
                Mth.lerp(pt, entity.zOld, entity.getZ()));

        if (entity.isRemoved()) { TRAILS.remove(entity.getId()); return; }

        ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>());
        trail.addFirst(curPos);
        while (trail.size() > TRAIL_MAX) trail.removeLast();

        float tick = (entity.tickCount + pt) * 0.05f;

        // ── 渲染主球体 & 光圈 ──
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.scale(3f, 3f, 3f);
        renderSphere(ps, buf, SPHERE_R, 0.95f);
        renderGlowRing(ps, buf, tick, RING_OUTER, RING_INNER, 0.9f);

        ps.popPose();

        super.render(entity, yaw, pt, ps, buf, light);
    }

    private void renderSphere(PoseStack ps, MultiBufferSource buf, float radius, float alpha) {
        Matrix4f mat = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(ORB_SPHERE);

        vc.vertex(mat, 0f, 0f, 0f).color(0f, 0f, 0f, alpha).endVertex();
        for (int i = 0; i <= SPHERE_SEG; i++) {
            double a  = (i / (double) SPHERE_SEG) * Math.PI * 2;
            float  vx = (float)(Math.cos(a) * radius);
            float  vy = (float)(Math.sin(a) * radius);
            vc.vertex(mat, vx, vy, 0f).color(0f, 0f, 0f, alpha).endVertex();
        }
    }

    private void renderGlowRing(PoseStack ps, MultiBufferSource buf,
                                float tick,
                                float outerR, float innerR, float alpha) {
        Matrix4f    mat = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(ORB_RING);

        float yr = 1.0f, yg = 0.85f, yb = 0.0f;

        for (int i = 0; i <= RING_SEG; i++) {
            double angle = (i / (double) RING_SEG) * Math.PI * 2;

            float ox = (float)(Math.cos(angle) * outerR);
            float oy = (float)(Math.sin(angle) * outerR);
            float ix = (float)(Math.cos(angle) * innerR);
            float iy = (float)(Math.sin(angle) * innerR);

            float flicker = 0.8f + 0.2f * (float) Math.sin(angle * 3 + tick * 4.0f);

            vc.vertex(mat, ox, oy, 0f).color(yr * flicker, yg * flicker, yb, alpha).endVertex();
            vc.vertex(mat, ix, iy, 0f).color(yr * flicker * 0.2f, yg * flicker * 0.2f, 0f, 0f).endVertex();
        }
    }
}