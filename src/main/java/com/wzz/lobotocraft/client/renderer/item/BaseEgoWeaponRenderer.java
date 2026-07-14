package com.wzz.lobotocraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.client.model.item.ego.BaseEgoWeaponModel;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.IAnimatablePerspective;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * E.G.O武器通用渲染器
 */
public class BaseEgoWeaponRenderer extends GeoItemRenderer<BaseEgoWeapon> {

    public BaseEgoWeaponRenderer(String weaponName, String postFix) {
        super(new BaseEgoWeaponModel(weaponName, postFix));
    }

    public BaseEgoWeaponRenderer(String weaponName) {
        this(weaponName, null);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, BaseEgoWeapon animatable,
                               BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        IAnimatablePerspective.Perspective perspective = convertToCustomPerspective(this.renderPerspective);
        if (!animatable.shouldPlayInPerspective(perspective)) {
            this.modelRenderTranslations = new Matrix4f(poseStack.last().pose());
            super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                    true, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private IAnimatablePerspective.Perspective convertToCustomPerspective(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> IAnimatablePerspective.Perspective.FIRST_PERSON_RIGHT;
            case FIRST_PERSON_LEFT_HAND -> IAnimatablePerspective.Perspective.FIRST_PERSON_LEFT;
            case THIRD_PERSON_RIGHT_HAND -> IAnimatablePerspective.Perspective.THIRD_PERSON_RIGHT;
            case THIRD_PERSON_LEFT_HAND -> IAnimatablePerspective.Perspective.THIRD_PERSON_LEFT;
            case GUI -> IAnimatablePerspective.Perspective.GUI;
            case GROUND -> IAnimatablePerspective.Perspective.GROUND;
            case FIXED -> IAnimatablePerspective.Perspective.FIXED;
            case HEAD -> IAnimatablePerspective.Perspective.HEAD;
            default -> IAnimatablePerspective.Perspective.FIRST_PERSON_RIGHT;
        };
    }
}