package com.wzz.lobotocraft.item.base;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;

public interface IBodyPartRenderer {

    /**
     * 获取渲染的身体部位类型
     */
    default BodyPartType getBodyPartType() {
        return BodyPartType.HEAD;
    }
    
    /**
     * 应用该部位的默认变换（可选override）
     */
    default void applyTransform(PoseStack poseStack, HumanoidModel<?> model, BodyPartType part) {
        part.applyDefaultTransform(poseStack, model);
    }
    
    enum BodyPartType {
        HEAD {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.head.translateAndRotate(poseStack);
                poseStack.translate(0.0F, -0.25F, 0.0F);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(0.625F, -0.625F, -0.625F);
            }
        },
        
        RIGHT_ARM {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.rightArm.translateAndRotate(poseStack);
                poseStack.translate(-0.025F, 0.625F,-0.125F);
                poseStack.scale(0.85F, 0.625F, 0.925F);
            }
        },
        
        LEFT_ARM {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.leftArm.translateAndRotate(poseStack);
                poseStack.translate(-0.025F, 0.625F,-0.125F);
                poseStack.scale(-0.85F, 0.625F, -0.925F);
            }
        },
        
        BODY {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.body.translateAndRotate(poseStack);
                poseStack.scale(1.0F, 1.0F, 1.0F);
            }
        },
        
        RIGHT_LEG {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.rightLeg.translateAndRotate(poseStack);
                poseStack.translate(0.0F, 0.75F, 0.0F);
                poseStack.scale(0.625F, 0.625F, 0.625F);
            }
        },
        
        LEFT_LEG {
            @Override
            public void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model) {
                model.leftLeg.translateAndRotate(poseStack);
                poseStack.translate(0.0F, 0.75F, 0.0F);
                poseStack.scale(0.625F, 0.625F, 0.625F);
            }
        };
        
        public abstract void applyDefaultTransform(PoseStack poseStack, HumanoidModel<?> model);
    }
}