package com.wzz.lobotocraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.client.model.item.ego.BaseEgoCurioModel;
import com.wzz.lobotocraft.item.base.IBodyPartRenderer;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class BaseEgoCurioRenderer extends GeoItemRenderer<BaseEgoCurio> implements ICurioRenderer {
    private final ItemInHandRenderer itemRenderer = Minecraft.getInstance()
            .getEntityRenderDispatcher().getItemInHandRenderer();

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

        if (wearer != null && wearer.level.isClientSide) {
            boolean isMoving = Math.abs(wearer.zza) > 0.01 || Math.abs(wearer.xxa) > 0.01;
            BaseEgoCurio.WEARER_MOVING.put(wearer.getId(), isMoving);
            BaseEgoCurio.currentRenderEntityId = wearer.getId();
            BaseEgoCurio.isRenderingAsCurio = true;
        }

        IBodyPartRenderer.BodyPartType partType = curio.getBodyPartType();
        poseStack.pushPose();
        curio.applyTransform(poseStack, humanoid, partType);
        if (wearer != null) {
            itemRenderer.renderItem(wearer, stack, getDisplayContext(partType),
                    false, poseStack, buffer, light);
        }
        poseStack.popPose();
        BaseEgoCurio.isRenderingAsCurio = false;
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
