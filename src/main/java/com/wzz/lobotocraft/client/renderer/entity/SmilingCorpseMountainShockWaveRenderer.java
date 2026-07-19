package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.entity.EntitySmilingCorpseMountainShockWave;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 把冲击波实体渲染成一张"平铺在地面"的魔法阵：
 *  - 贴图逐帧循环 tex_0 .. tex_6, 共播放 LOOPS 次;
 *  - 四边形整体从小到大缓慢放大(配合帧动画呈现扩散感);
 *  - 末尾淡出。
 * 贴图路径: assets/lobotocraft/textures/entities/smiling_corpse_mountain_shock_wave/tex_N.png (32x32)
 */
public class SmilingCorpseMountainShockWaveRenderer
        extends EntityRenderer<EntitySmilingCorpseMountainShockWave> {

    /** 魔法阵最终半径(格),直径约 3.2 格。想更大/更小改这里 */
    private static final float MAX_RADIUS = 1.6F;
    /** 起始缩放比例(相对最终) */
    private static final float START_SCALE = 0.7F;
    /** 离地高度,避免与地面 z-fighting */
    private static final float Y_OFFSET = 0.03F;

    public SmilingCorpseMountainShockWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation frameTex(int frame) {
        return ResourceUtil.createInstance(
                "textures/entities/smiling_corpse_mountain_shock_wave/tex_" + frame + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySmilingCorpseMountainShockWave entity) {
        return frameTex(0);
    }

    @Override
    public void render(EntitySmilingCorpseMountainShockWave entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        float age = entity.tickCount + partialTicks;
        float life = EntitySmilingCorpseMountainShockWave.LIFETIME;
        if (age >= life) return;

        // 当前帧: 在 (FRAMES * LOOPS) 个刻度块里循环
        int totalSteps = EntitySmilingCorpseMountainShockWave.FRAMES * EntitySmilingCorpseMountainShockWave.LOOPS;
        int step = (int) (age / EntitySmilingCorpseMountainShockWave.FRAME_TICKS);
        if (step >= totalSteps) step = totalSteps - 1;
        int frame = step % EntitySmilingCorpseMountainShockWave.FRAMES;

        // 进度 0..1 -> 缩放 & 淡出
        float progress = Math.min(1F, age / life);
        float scale = START_SCALE + (1F - START_SCALE) * progress;
        float radius = MAX_RADIUS * scale;
        float alpha = progress > 0.8F ? Math.max(0F, 1F - (progress - 0.8F) / 0.2F) : 1F;

        poseStack.pushPose();
        // render() 传入的 poseStack 原点已在实体插值位置(相机相对), 直接在 XZ 平面画一张平铺四边形
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(frameTex(frame)));
        int light = LightTexture.FULL_BRIGHT;

        // 上表面(从上往下看可见)
        quad(vc, matrix, normal, radius, alpha, light, false);
        // 下表面(从下往上看也可见, 防止某些视角被剔除)
        quad(vc, matrix, normal, radius, alpha, light, true);

        poseStack.popPose();
        // 故意不调用 super.render(): 该实体没有名字/阴影需求
    }

    private static void quad(VertexConsumer vc, Matrix4f matrix, Matrix3f normal,
                             float r, float alpha, int light, boolean flip) {
        float y = Y_OFFSET;
        float ny = flip ? -1F : 1F;
        if (!flip) {
            vertex(vc, matrix, normal, -r, y, -r, 0F, 0F, alpha, light, ny);
            vertex(vc, matrix, normal, -r, y,  r, 0F, 1F, alpha, light, ny);
            vertex(vc, matrix, normal,  r, y,  r, 1F, 1F, alpha, light, ny);
            vertex(vc, matrix, normal,  r, y, -r, 1F, 0F, alpha, light, ny);
        } else {
            vertex(vc, matrix, normal,  r, y, -r, 1F, 0F, alpha, light, ny);
            vertex(vc, matrix, normal,  r, y,  r, 1F, 1F, alpha, light, ny);
            vertex(vc, matrix, normal, -r, y,  r, 0F, 1F, alpha, light, ny);
            vertex(vc, matrix, normal, -r, y, -r, 0F, 0F, alpha, light, ny);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               float alpha, int light, float ny) {
        vc.vertex(matrix, x, y, z)
                .color(1F, 1F, 1F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0F, ny, 0F)
                .endVertex();
    }
}
