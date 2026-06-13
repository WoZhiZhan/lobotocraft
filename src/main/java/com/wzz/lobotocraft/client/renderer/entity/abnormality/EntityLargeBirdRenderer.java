package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class EntityLargeBirdRenderer extends AbnormalityRenderer<EntityLargeBird> {
    public EntityLargeBirdRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
    }

    public EntityLargeBirdRenderer(EntityRendererProvider.Context renderManager, float height, float width) {
        super(renderManager, height, width);
    }

    @Override
    public void render(EntityLargeBird entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int light = packedLight;
        if (entity.hasEscape() && entity.getCurrentAnimation().contains("model.3")) {
            light = 15728880;
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(EntityLargeBird animatable) {
        if (animatable.hasEscape() && animatable.getCurrentAnimation().contains("model.3")) {
            return ResourceUtil.createInstance("textures/entities/large_bird_angry.png");
        }
        return super.getTextureLocation(animatable);
    }
}
