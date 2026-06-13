package com.wzz.lobotocraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SimpleBlockEntityRenderer implements BlockEntityRenderer<BlockEntity> {
    private final float scale;

    public SimpleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this(context, 1f);
    }

    public SimpleBlockEntityRenderer(BlockEntityRendererProvider.Context context, float scale) {
        this.scale = scale;
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        BlockState state = blockEntity.getBlockState();
        BakedModel bakedModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        poseStack.scale(this.scale, this.scale, this.scale);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                state,
                bakedModel,
                1.0f, 1.0f, 1.0f,
                packedLight,
                packedOverlay
        );
        poseStack.popPose();
    }
}