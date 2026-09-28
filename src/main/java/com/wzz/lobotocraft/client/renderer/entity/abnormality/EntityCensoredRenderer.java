package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.model.entity.CensoredModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.math.Axis;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class EntityCensoredRenderer extends AbnormalityRenderer<EntityCensored> {
    public EntityCensoredRenderer(EntityRendererProvider.Context context) {
        // Same height/width rendering scale as the judgement bird registration.
        super(context, new CensoredModel(), 1.5F, 2.0F);
    }

    @Override
    public void preRender(PoseStack poseStack, EntityCensored entity, BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        // The source model's head and neck point along +X; Minecraft mobs face +Z at yaw 0.
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
