package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.model.entity.BaseModel;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

@OnlyIn(Dist.CLIENT)
public class AbnormalityRenderer<T extends AbstractAbnormality> extends GeoEntityRenderer<T> {
    private final float height;
    private final float width;
    public AbnormalityRenderer(EntityRendererProvider.Context renderManager) {
        this(renderManager, 0.65f);
    }

    public AbnormalityRenderer(EntityRendererProvider.Context renderManager, GeoModel<T> model) {
        super(renderManager, model);
        this.shadowRadius = 0.65F;
        this.height = 1.0f;
        this.width = 1.0f;
    }

    public AbnormalityRenderer(EntityRendererProvider.Context renderManager, float scale) {
        this(renderManager, scale, scale);
    }

    public AbnormalityRenderer(EntityRendererProvider.Context renderManager, float height, float width) {
        super(renderManager, new BaseModel<>());
        this.shadowRadius = 0.65F;
        this.height = height;
        this.width = width;
    }

    public AbnormalityRenderer(EntityRendererProvider.Context renderManager, String texture, String name, float height, float width) {
        super(renderManager, new BaseModel<>(name, texture));
        this.shadowRadius = 0.65F;
        this.height = height;
        this.width = width;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(((GeoRenderer<T>) this).getTextureLocation(animatable));
    }

    @Override
    public void preRender(PoseStack poseStack, T entity, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.scaleHeight = height;
        this.scaleWidth = width;
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    protected void renderNameTag(T p_114498_, Component p_114499_, PoseStack p_114500_, MultiBufferSource p_114501_, int p_114502_) {
        //super.renderNameTag(p_114498_, p_114499_, p_114500_, p_114501_, p_114502_);
    }

    @Override
    protected float getDeathMaxRotation(T entityLivingBaseIn) {
        return 0.0F;
    }
}