package com.wzz.lobotocraft.client.renderer.entity;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class EntityClerkRenderer extends MobRenderer<EntityClerk, PlayerModel<EntityClerk>> {
    public EntityClerkRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(EntityClerk entity) {
        return ResourceUtil.createInstance("textures/entities/clerk/clerk_" + entity.getTextureVariant() + ".png");
    }
}
