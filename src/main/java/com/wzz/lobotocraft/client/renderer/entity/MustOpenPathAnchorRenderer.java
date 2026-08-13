package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.entity.projectile.MustOpenPathAnchorProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the anchor along its flight vector instead of billboard-facing the camera. */
public final class MustOpenPathAnchorRenderer extends EntityRenderer<MustOpenPathAnchorProjectile> {
    private final ItemRenderer itemRenderer;

    public MustOpenPathAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MustOpenPathAnchorProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Vec3 direction = entity.getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = entity.getViewVector(partialTick);
        }
        direction = direction.normalize();

        poseStack.pushPose();
        poseStack.scale(0.75F, 0.75F, 0.75F);
        Vector3f localTipDirection = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f flightDirection = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        poseStack.mulPose(localTipDirection.rotationTo(flightDirection, new Quaternionf()));
        itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.NONE, packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MustOpenPathAnchorProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
