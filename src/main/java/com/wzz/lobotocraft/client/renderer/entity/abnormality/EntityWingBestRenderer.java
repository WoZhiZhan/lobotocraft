package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityWingBeat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class EntityWingBestRenderer extends AbnormalityRenderer<EntityWingBeat> {
    public EntityWingBestRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
    }

    public EntityWingBestRenderer(EntityRendererProvider.Context renderManager, float height, float width) {
        super(renderManager, height, width);
    }

    @Override
    public void render(EntityWingBeat entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        if (entity.isSmall()) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
