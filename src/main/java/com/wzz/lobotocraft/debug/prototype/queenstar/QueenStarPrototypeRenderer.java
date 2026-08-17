package com.wzz.lobotocraft.debug.prototype.queenstar;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QueenStarPrototypeRenderer {
    private static final ResourceLocation CORE_TEXTURE =
            ResourceUtil.createInstance("textures/effect/queen_star/star_core.png");
    private static final ResourceLocation OUTLINE_TEXTURE =
            ResourceUtil.createInstance("textures/effect/queen_star/star_outline.png");
    private static final ResourceLocation FILL_TEXTURE =
            ResourceUtil.createInstance("textures/effect/queen_star/star_fill.png");
    private static final ResourceLocation THRUSTER_TEXTURE =
            ResourceUtil.createInstance("textures/effect/queen_star/space_thruster.png");
    private static final int PARTICLES_PER_GROUP = 8;
    private static final float CORE_HALF_SIZE = 0.68F;
    private static final float CORE_DEPTH = 0.13F;

    private QueenStarPrototypeRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.level != null && !minecraft.isPaused()) {
            QueenStarPrototypeManager.tick();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            QueenStarPrototypeManager.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        QueenStarPrototypeManager.Shot shot = QueenStarPrototypeManager.getActive();
        if (shot == null) return;

        float partialTick = event.getPartialTick();
        float impact = shot.impactProgress(partialTick);
        float alpha = 1.0F - impact;
        Vec3 position = shot.position(partialTick);
        float time = shot.ageTicks + partialTick;
        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        try {
            renderCoreSides(poseStack, cameraPosition, position, shot.direction, time, alpha);

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            renderTrail(poseStack, cameraPosition, position, shot.direction, alpha);
            renderParticleGroup(poseStack, camera, cameraPosition, position,
                    OUTLINE_TEXTURE, time, alpha, 0, true);
            renderParticleGroup(poseStack, camera, cameraPosition, position,
                    FILL_TEXTURE, time, alpha, 1, true);
            renderParticleGroup(poseStack, camera, cameraPosition, position,
                    OUTLINE_TEXTURE, time, alpha, 2, false);
            renderParticleGroup(poseStack, camera, cameraPosition, position,
                    FILL_TEXTURE, time, alpha, 3, false);
            renderCoreFace(poseStack, cameraPosition, position, shot.direction, time, alpha);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void renderCoreSides(PoseStack poseStack, Vec3 cameraPosition,
                                        Vec3 position, Vec3 direction, float time, float alpha) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        beginCorePose(poseStack, cameraPosition, position, direction, time);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        float frontZ = CORE_DEPTH * 0.5F;
        float backZ = -frontZ;
        for (int i = 0; i < 10; i++) {
            Point first = contourPoint(i);
            Point second = contourPoint((i + 1) % 10);
            Color front = new Color(0.92F, 1.0F, 0.05F, alpha * 0.96F);
            Color back = new Color(0.78F, 0.06F, 0.38F, alpha * 0.88F);
            vertex(builder, matrix, first.x, first.y, frontZ, front);
            vertex(builder, matrix, second.x, second.y, frontZ, front);
            vertex(builder, matrix, second.x, second.y, backZ, back);
            vertex(builder, matrix, first.x, first.y, backZ, back);
        }
        tesselator.end();
        poseStack.popPose();
    }

    private static void renderCoreFace(PoseStack poseStack, Vec3 cameraPosition,
                                       Vec3 position, Vec3 direction, float time, float alpha) {
        beginCorePose(poseStack, cameraPosition, position, direction, time);
        drawTexturedQuad(poseStack.last().pose(), CORE_TEXTURE,
                -CORE_HALF_SIZE, -CORE_HALF_SIZE, CORE_HALF_SIZE, CORE_HALF_SIZE,
                CORE_DEPTH * 0.5F + 0.002F,
                new Color(0.92F, 1.0F, 0.08F, alpha * 0.96F));
        poseStack.popPose();
    }

    private static void renderTrail(PoseStack poseStack, Vec3 cameraPosition,
                                    Vec3 position, Vec3 direction, float alpha) {
        Vec3 toCamera = cameraPosition.subtract(position);
        Vec3 side = direction.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6D) {
            Vec3 reference = Math.abs(direction.y) > 0.92D
                    ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            side = direction.cross(reference);
        }
        side = side.normalize().scale(0.39D);
        Vec3 tail = position.subtract(direction.scale(1.72D));
        Vec3 head = position.add(direction.scale(0.10D));
        Vec3 tailLower = tail.subtract(side).subtract(cameraPosition);
        Vec3 headLower = head.subtract(side).subtract(cameraPosition);
        Vec3 headUpper = head.add(side).subtract(cameraPosition);
        Vec3 tailUpper = tail.add(side).subtract(cameraPosition);

        RenderSystem.setShaderTexture(0, THRUSTER_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        Matrix4f matrix = poseStack.last().pose();
        Color color = new Color(0.90F, 1.0F, 0.08F, alpha * 0.74F);
        vertex(builder, matrix, tailLower, color, 0.0F, 1.0F);
        vertex(builder, matrix, headLower, color, 1.0F, 1.0F);
        vertex(builder, matrix, headUpper, color, 1.0F, 0.0F);
        vertex(builder, matrix, tailUpper, color, 0.0F, 0.0F);
        tesselator.end();
    }

    private static void renderParticleGroup(PoseStack poseStack, Camera camera, Vec3 cameraPosition,
                                            Vec3 center, ResourceLocation texture, float time,
                                            float globalAlpha, int group, boolean warm) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        for (int i = 0; i < PARTICLES_PER_GROUP; i++) {
            float lifetime = 10.0F + hash(group, i, 1) * 20.0F;
            float offset = hash(group, i, 2) * lifetime;
            float cycle = (time + offset) / lifetime;
            int generation = Mth.floor(cycle);
            float age = cycle - generation;
            float fadeIn = Mth.clamp(age / 0.14F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((1.0F - age) / 0.30F, 0.0F, 1.0F);
            float particleAlpha = fadeIn * fadeOut * globalAlpha;
            if (particleAlpha <= 0.01F) continue;

            float angle = hash(group, i, generation * 3 + 3) * Mth.TWO_PI;
            float radius = Mth.lerp(hash(group, i, generation * 5 + 4), 0.18F, 0.48F);
            float vertical = Mth.lerp(hash(group, i, generation * 7 + 5), 0.72F, 1.0F);
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle) * radius * vertical;
            float size = Mth.lerp(hash(group, i, generation * 11 + 6), 0.105F, 0.205F)
                    * Mth.clamp(age / 0.15F, 0.0F, 1.0F);
            float rotation = (hash(group, i, generation * 13 + 7) - 0.5F) * 1.4F
                    + time * (hash(group, i, 8) - 0.5F) * 0.045F;
            float mix = hash(group, i, generation * 17 + 9);
            Color color = warm
                    ? mix(new Color(0.90F, 1.0F, 0.0F, particleAlpha),
                    new Color(1.0F, 0.0F, 0.93F, particleAlpha), mix)
                    : mix(new Color(0.0F, 1.0F, 0.09F, particleAlpha),
                    new Color(0.0F, 0.83F, 1.0F, particleAlpha), mix);

            poseStack.pushPose();
            Vec3 relative = center.subtract(cameraPosition);
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.mulPose(camera.rotation());
            poseStack.translate(x, y, -0.02F - group * 0.001F);
            poseStack.mulPose(Axis.ZP.rotation(rotation));
            emitTexturedQuad(builder, poseStack.last().pose(), -size, -size, size, size,
                    0.0F, color);
            poseStack.popPose();
        }
        tesselator.end();
    }

    private static void beginCorePose(PoseStack poseStack, Vec3 cameraPosition,
                                      Vec3 position, Vec3 direction, float time) {
        poseStack.pushPose();
        Vec3 relative = position.subtract(cameraPosition);
        poseStack.translate(relative.x, relative.y, relative.z);
        Quaternionf flightOrientation = new Quaternionf().rotationTo(
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        poseStack.mulPose(flightOrientation);
        poseStack.mulPose(Axis.ZP.rotation(time * 0.13F));
    }

    private static void drawTexturedQuad(Matrix4f matrix, ResourceLocation texture,
                                         float minX, float minY, float maxX, float maxY,
                                         float z, Color color) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        emitTexturedQuad(builder, matrix, minX, minY, maxX, maxY, z, color);
        tesselator.end();
    }

    private static void emitTexturedQuad(BufferBuilder builder, Matrix4f matrix,
                                         float minX, float minY, float maxX, float maxY,
                                         float z, Color color) {
        vertex(builder, matrix, minX, minY, z, color, 0.0F, 1.0F);
        vertex(builder, matrix, maxX, minY, z, color, 1.0F, 1.0F);
        vertex(builder, matrix, maxX, maxY, z, color, 1.0F, 0.0F);
        vertex(builder, matrix, minX, maxY, z, color, 0.0F, 0.0F);
    }

    private static Point contourPoint(int index) {
        float angle = -Mth.HALF_PI + Mth.TWO_PI * index / 10.0F;
        float radius = (index & 1) == 0 ? 0.59F : 0.37F;
        return new Point(Mth.cos(angle) * radius, Mth.sin(angle) * radius);
    }

    private static float hash(int group, int index, int salt) {
        int value = group * 0x45d9f3b + index * 0x27d4eb2d + salt * 0x165667b1;
        value = (value ^ (value >>> 16)) * 0x45d9f3b;
        value ^= value >>> 16;
        return (value & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    private static Color mix(Color first, Color second, float amount) {
        return new Color(Mth.lerp(amount, first.red, second.red),
                Mth.lerp(amount, first.green, second.green),
                Mth.lerp(amount, first.blue, second.blue),
                Mth.lerp(amount, first.alpha, second.alpha));
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z, Color color) {
        builder.vertex(matrix, x, y, z)
                .color(color.red, color.green, color.blue, color.alpha)
                .endVertex();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z, Color color, float u, float v) {
        builder.vertex(matrix, x, y, z)
                .color(color.red, color.green, color.blue, color.alpha)
                .uv(u, v)
                .endVertex();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               Vec3 position, Color color, float u, float v) {
        vertex(builder, matrix, (float) position.x, (float) position.y, (float) position.z,
                color, u, v);
    }

    private record Point(float x, float y) {
    }

    private record Color(float red, float green, float blue, float alpha) {
    }
}
