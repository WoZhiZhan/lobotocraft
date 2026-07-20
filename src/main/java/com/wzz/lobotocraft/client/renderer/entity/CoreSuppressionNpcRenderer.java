package com.wzz.lobotocraft.client.renderer.entity;

import com.wzz.lobotocraft.entity.EntityCoreSuppressionNpc;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CoreSuppressionNpcRenderer
        extends MobRenderer<EntityCoreSuppressionNpc, PlayerModel<EntityCoreSuppressionNpc>> {

    public CoreSuppressionNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.45F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(EntityCoreSuppressionNpc entity) {
        var type = entity.getCoreSuppressionType();
        String id = type == null ? "malkuth" : type.getId();
        return ResourceUtil.createInstance("textures/entities/core_suppression/" + id + ".png");
    }
}
