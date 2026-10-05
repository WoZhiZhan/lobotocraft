package com.wzz.lobotocraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.core.GeburahBladeTrailProfile;
import com.wzz.lobotocraft.client.core.GeburahBladeTrailRenderer;
import com.wzz.lobotocraft.client.core.GeburahBladeTrailState;
import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahPhase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class GeburahRenderer extends GeoEntityRenderer<EntityGeburah> {
    public GeburahRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, 1.0F);
        addRenderLayer(new BladeCaptureLayer(this));
    }

    private static final class BladeCaptureLayer extends GeoRenderLayer<EntityGeburah> {
        private BladeCaptureLayer(GeburahRenderer renderer) {
            super(renderer);
        }

        @Override
        public void preRender(PoseStack poseStack, EntityGeburah entity,
                              BakedGeoModel bakedModel, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
            boolean hideBuiltInTrails = GeburahBladeTrailRenderer.debugMarkers()
                    && !GeburahBladeTrailRenderer.trailSurfaceEnabled();
            for (String boneName : List.of(
                    "nitai2slashlight", "nitai2slashlight2", "greatknifelight")) {
                bakedModel.getBone(boneName)
                        .ifPresent(bone -> bone.setHidden(hideBuiltInTrails));
            }
            GeburahBladeTrailProfile.Sample sample =
                    GeburahBladeTrailProfile.forAnimation(entity.getAnimation());
            if (sample == null) return;
            for (String locatorBoneName : sample.locatorBoneNames()) {
                bakedModel.getBone(locatorBoneName)
                        .ifPresent(bone -> bone.setTrackingMatrices(true));
            }
        }

        @Override
        public void render(PoseStack poseStack, EntityGeburah entity,
                           BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            GeburahBladeTrailProfile.Sample sample =
                    GeburahBladeTrailProfile.forAnimation(entity.getAnimation());
            if (sample == null) return;
            Vec3 currentEntityPosition = entity.position();
            Vec3 interpolatedEntityPosition = new Vec3(
                    Mth.lerp(partialTick, entity.xOld, entity.getX()),
                    Mth.lerp(partialTick, entity.yOld, entity.getY()),
                    Mth.lerp(partialTick, entity.zOld, entity.getZ()));
            Vec3 interpolationOffset = interpolatedEntityPosition.subtract(currentEntityPosition);
            List<Vec3> worldPositions = new ArrayList<>(sample.locatorBoneNames().size());
            for (String locatorBoneName : sample.locatorBoneNames()) {
                GeoBone locatorBone = bakedModel.getBone(locatorBoneName).orElse(null);
                if (locatorBone == null) return;
                Vector3d position = locatorBone.getWorldPosition();
                worldPositions.add(new Vec3(position.x, position.y, position.z)
                        .add(interpolationOffset));
            }
            double sampleTime = entity.level().getGameTime() + partialTick;
            GeburahBladeTrailState.capture(entity.getId(), sampleTime, worldPositions,
                    sample.ego(), sample.move(), ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.get()
                            && GeburahEgoAttackVisuals.animationEligible(
                            entity.getAnimation(), entity.syncedPhase() == GeburahPhase.EGO));
        }
    }
}
