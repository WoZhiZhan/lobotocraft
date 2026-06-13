package com.wzz.lobotocraft.client.renderer.entity;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class EmptyEntityRenderer extends EntityRenderer<Entity> {
    public EmptyEntityRenderer(EntityRendererProvider.Context p_174008_) {
        super(p_174008_);
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return ResourceUtil.createEmptyTexture();
    }
}
