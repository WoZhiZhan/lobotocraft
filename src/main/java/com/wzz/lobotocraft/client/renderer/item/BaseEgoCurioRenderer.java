package com.wzz.lobotocraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.model.item.FlatCurioModel;
import com.wzz.lobotocraft.client.model.item.ego.BaseEgoCurioModel;
import com.wzz.lobotocraft.item.base.IBodyPartRenderer;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class BaseEgoCurioRenderer extends GeoItemRenderer<BaseEgoCurio> implements ICurioRenderer {

    public BaseEgoCurioRenderer(String curioName) {
        super(new BaseEgoCurioModel(curioName));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> parentModel, MultiBufferSource buffer,
            int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch) {

        if (!(parentModel.getModel() instanceof HumanoidModel<?> humanoid)) return;
        if (!(stack.getItem() instanceof BaseEgoCurio curio)) return;
        if (!curio.rendersOnWearer()) return;

        LivingEntity wearer = slotContext.entity();
        if (wearer == null) return;

        if (wearer.level.isClientSide) {
            boolean isMoving = Math.abs(wearer.zza) > 0.01 || Math.abs(wearer.xxa) > 0.01;
            BaseEgoCurio.WEARER_MOVING.put(wearer.getId(), isMoving);
            BaseEgoCurio.currentRenderEntityId = wearer.getId();
        }

        // 取 json 里的 display（被替换过的模型要拿原始的那份）
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        ItemTransforms transforms = (model instanceof FlatCurioModel f)
                ? f.getWearTransforms()
                : model.getTransforms();

        IBodyPartRenderer.BodyPartType partType = curio.getBodyPartType();
        ItemDisplayContext ctx = getDisplayContext(partType);

        poseStack.pushPose();
        curio.applyTransform(poseStack, humanoid, partType);

        // 复现 ItemRenderer.render 的两步：套 display + 居中
        transforms.getTransform(ctx).apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        BaseEgoCurio.isRenderingAsCurio = true;
        try {
            super.renderByItem(stack, ctx, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        } finally {
            BaseEgoCurio.isRenderingAsCurio = false;
        }
        poseStack.popPose();
    }

    private ItemDisplayContext getDisplayContext(IBodyPartRenderer.BodyPartType partType) {
        return switch (partType) {
            case HEAD -> ItemDisplayContext.HEAD;
            case RIGHT_ARM -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case LEFT_ARM -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            default -> ItemDisplayContext.FIXED;
        };
    }
}