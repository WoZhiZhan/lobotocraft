package com.wzz.lobotocraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyRenderContext;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 圣宣专用渲染器：同一个物品、同一个模型，按 NBT 切黑白贴图。
 */
public class ButterflyFuneralWeaponRenderer extends BaseEgoWeaponRenderer {

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceUtil.createInstance(ModMain.MODID, "textures/weapon/butterfly_funeral_weapon.png");
    private static final ResourceLocation BLACK_TEXTURE =
            ResourceUtil.createInstance(ModMain.MODID, "textures/weapon/butterfly_funeral_weapon_black.png");

    public ButterflyFuneralWeaponRenderer() {
        super("butterfly_funeral");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 记录当前渲染的这把枪：贴图选择 + 动画 predicate 都要用
        ButterflyRenderContext.CURRENT = stack;
        try {
            super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            ButterflyRenderContext.CURRENT = ItemStack.EMPTY;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BaseEgoWeapon animatable) {
        return ButterflyFuneralWeapon.isBlack(ButterflyRenderContext.CURRENT) ? BLACK_TEXTURE : WHITE_TEXTURE;
    }
}