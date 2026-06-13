package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityFourthMatchFlame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class EntityFourthMatchFlameRenderer extends AbnormalityRenderer<EntityFourthMatchFlame> {
    public EntityFourthMatchFlameRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
    }

    public EntityFourthMatchFlameRenderer(EntityRendererProvider.Context renderManager, float height, float width) {
        super(renderManager, height, width);
    }

    @Override
    public void render(EntityFourthMatchFlame entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int light = packedLight;
        if (entity.isExploding()) {
            light = 15728880;
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }
}
