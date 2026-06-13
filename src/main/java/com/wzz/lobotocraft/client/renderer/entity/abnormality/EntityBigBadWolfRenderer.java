package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.model.entity.BaseModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityBigBadWolf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

/**
 * 又大又可能很坏的狼的双模型渲染器(参考 EntityPpodaeRenderer)。
 * 出逃前使用 bigbadwolf 模型,出逃后切换为 bigbadwolf_big 模型。
 * 渲染大小:Ppodae 方案中 scaleHeight/scaleWidth 在双模型切换下不生效,
 * 这里改为在 preRender 中通过 poseStack 显式缩放,保证两个形态的大小均可调。
 */
public class EntityBigBadWolfRenderer extends AbnormalityRenderer<EntityBigBadWolf> {

    private EntityBigBadWolf wolf;
    private final GeoModel<EntityBigBadWolf> normalModel;
    private final GeoModel<EntityBigBadWolf> escapeModel;
    private final float normalScale;
    private final float escapeScale;

    public EntityBigBadWolfRenderer(EntityRendererProvider.Context renderManager,
                                    float normalScale, float escapeScale) {
        super(renderManager, 1.0f, 1.0f);
        this.normalModel = new BaseModel<>("bigbadwolf", null);
        this.escapeModel = new BaseModel<>("bigbadwolf_big", null);
        this.normalScale = normalScale;
        this.escapeScale = escapeScale;
    }

    @Override
    public void preRender(PoseStack poseStack, EntityBigBadWolf entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        this.wolf = entity;
        // 显式缩放(修复 scaleHeight/scaleWidth 在多模型切换下不生效的问题)
        float s = entity.hasEscape() ? escapeScale : normalScale;
        poseStack.scale(s, s, s);
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public GeoModel<EntityBigBadWolf> getGeoModel() {
        if (this.wolf != null && this.wolf.hasEscape()) {
            return this.escapeModel;
        }
        return this.normalModel;
    }
}
