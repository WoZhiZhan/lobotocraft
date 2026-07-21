package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.entity.EntityText;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class EntityTextRenderer extends EntityRenderer<EntityText> {
    private static final int FADE_IN_TICKS = 10;   // 淡入时长
    private static final int FADE_OUT_TICKS = 10;  // 淡出时长

    private final Font font;

    public EntityTextRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
        this.font = context.getFont();
    }

    @Override
    public void render(EntityText entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        String text = entity.getText();
        if (text == null || text.isEmpty()) return;

        int max = entity.getMaxLifeTick();
        float age = entity.tickCount + partialTick;
        float remaining = max - age;

        int fadeIn = Math.min(FADE_IN_TICKS, max / 2);
        int fadeOut = Math.min(FADE_OUT_TICKS, max / 2);

        float alpha = 1.0f;
        if (fadeIn > 0 && age < fadeIn) {
            alpha = age / fadeIn;
        } else if (fadeOut > 0 && remaining < fadeOut) {
            alpha = remaining / fadeOut;
        }
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        int alphaByte = (int) (alpha * 255.0f);
        if (alphaByte < 5) alphaByte = 5; // 低于 ~4，Font 会强制变成不透明，所以保底
        int rgb = entity.getColor() & 0xFFFFFF;
        int color = (alphaByte << 24) | rgb;

//        // 计算实体到摄像机的距离
//        Vec3 camPos = this.entityRenderDispatcher.camera.getPosition();
//        double dx = entity.getX() - camPos.x;
//        double dy = entity.getY() - camPos.y;
//        double dz = entity.getZ() - camPos.z;
//        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
//
//        poseStack.pushPose();
//        poseStack.translate(0.0, entity.getBbHeight() + 0.5, 0.0);
//        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
//
//        // 距离越远，缩放越大，抵消透视缩小 —— 屏幕上大小恒定
//        float baseScale = 0.025f;
//        float scale = baseScale * Math.max(1.0f, distance * 0.5f); // 0.5 是灵敏度，可调
//        poseStack.scale(-scale, -scale, scale);

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.5, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.getRotation()));
        poseStack.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0f;

        // SEE_THROUGH：关闭深度测试 —— 无视方块阻挡，穿墙可见
        font.drawInBatch(text, x, 0.0f, color, false, matrix, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(EntityText p_114491_, Frustum p_114492_, double p_114493_, double p_114494_, double p_114495_) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityText entity) {
        return ResourceUtil.createEmptyTexture();
    }
}