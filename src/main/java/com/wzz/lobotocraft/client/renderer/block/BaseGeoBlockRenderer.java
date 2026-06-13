package com.wzz.lobotocraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.block.entity.BaseGeoBlockEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class BaseGeoBlockRenderer extends GeoBlockRenderer<BaseGeoBlockEntity> {
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float scaleZ = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float offsetZ = 0f;

    public BaseGeoBlockRenderer(GeoModel<BaseGeoBlockEntity> model) {
        super(model);
    }

    public BaseGeoBlockRenderer withScale(float scaleX, float scaleY) {
        return withScale(scaleX, scaleY, scaleX);
    }

    public BaseGeoBlockRenderer withScale(float scaleX, float scaleY, float scaleZ) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        return this;
    }

    public BaseGeoBlockRenderer withOffset(float x, float y, float z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    BaseGeoBlockEntity animatable, BakedGeoModel model,
                                    boolean isReRender, float partialTick,
                                    int packedLight, int packedOverlay) {
        poseStack.scale(scaleX, scaleY, scaleZ);
        poseStack.translate(offsetX, offsetY, offsetZ);
    }

    @Override
    public boolean shouldRender(BaseGeoBlockEntity blockEntity, Vec3 cameraPos) {
        return true;
    }
}