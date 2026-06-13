package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.model.entity.EntityPpodaeEscapeModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityPpodae;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

public class EntityPpodaeRenderer extends AbnormalityRenderer<EntityPpodae> {
    private EntityPpodae ppodae;
    private final GeoModel<EntityPpodae> normalModel;
    private final GeoModel<EntityPpodae> escapeModel;

    public EntityPpodaeRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.normalModel = super.getGeoModel();
        this.escapeModel = new EntityPpodaeEscapeModel();
    }

    public EntityPpodaeRenderer(EntityRendererProvider.Context renderManager, float height, float width) {
        super(renderManager, height, width);
        this.normalModel = super.getGeoModel();
        this.escapeModel = new EntityPpodaeEscapeModel();
    }

    @Override
    public void preRender(PoseStack poseStack, EntityPpodae entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        this.ppodae = entity;
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public GeoModel<EntityPpodae> getGeoModel() {
        if (this.ppodae != null && this.ppodae.hasEscape()) {
            return this.escapeModel;
        }
        return this.normalModel;
    }
}