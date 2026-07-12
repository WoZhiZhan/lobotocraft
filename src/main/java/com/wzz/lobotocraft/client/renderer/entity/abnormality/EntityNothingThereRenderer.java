package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.model.entity.NothingThereModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityNothingThere;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class EntityNothingThereRenderer extends AbnormalityRenderer<EntityNothingThere> {

    public EntityNothingThereRenderer(EntityRendererProvider.Context renderManager, float scale) {
        super(renderManager, new NothingThereModel());
    }

    @Override
    public void preRender(PoseStack poseStack, EntityNothingThere entity, BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {

        if (entity.getCurrentPhase() == 1)
            poseStack.scale(2, 2, 2);

        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
