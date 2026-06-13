package com.wzz.lobotocraft.client.damage_border;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 伤害边框HUD渲染器
 * 当玩家受到伤害时显示对应颜色的边框，淡入淡出效果
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DamageBorderHud {
    private static final Minecraft minecraft = Minecraft.getInstance();

    // 边框材质路径（根据伤害类型）
    private static final ResourceLocation BORDER_RED = ResourceUtil.createInstance("textures/gui/border_red.png");
    private static final ResourceLocation BORDER_WHITE = ResourceUtil.createInstance("textures/gui/border_white.png");
    private static final ResourceLocation BORDER_BLACK = ResourceUtil.createInstance("textures/gui/border_black.png");
    private static final ResourceLocation BORDER_BLUE = ResourceUtil.createInstance("textures/gui/border_blue.png");

    // 当前活动的边框效果列表
    private static final List<DamageBorderEffect> activeBorders = new ArrayList<>();

    /**
     * 添加边框效果
     * @param damageType 伤害类型
     */
    public static void addBorderEffect(DamageBorderEffect.DamageType damageType) {
        // 移除同类型的旧效果，避免重复
        activeBorders.removeIf(effect -> effect.getDamageType() == damageType);
        // 添加新效果（300ms = 0.3秒）
        activeBorders.add(new DamageBorderEffect(damageType, 300));
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (minecraft.player == null) return;

        // 移除过期的效果
        activeBorders.removeIf(DamageBorderEffect::isExpired);

        // 渲染所有活动的边框效果
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        for (DamageBorderEffect effect : activeBorders) {
            renderBorder(guiGraphics, effect, screenWidth, screenHeight);
        }
    }

    /**
     * 渲染单个边框效果
     */
    private static void renderBorder(GuiGraphics guiGraphics, DamageBorderEffect effect, int screenWidth, int screenHeight) {
        // 获取对应的边框材质
        ResourceLocation borderTexture = getBorderTexture(effect.getDamageType());
        if (borderTexture == null) return;

        // 获取当前透明度
        int alpha = effect.getAlpha();
        if (alpha <= 0) return;

        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 设置颜色和透明度（RGBA）
        // 白色(1, 1, 1) + alpha
        float alphaFloat = alpha / 255.0f;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alphaFloat);

        // 渲染全屏边框
        // 假设边框材质是256x256，渲染到全屏
        guiGraphics.blit(borderTexture, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);

        // 恢复颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * 根据伤害类型获取对应的边框材质
     */
    private static ResourceLocation getBorderTexture(DamageBorderEffect.DamageType damageType) {
        return switch (damageType) {
            case RED -> BORDER_RED;
            case WHITE -> BORDER_WHITE;
            case BLACK -> BORDER_BLACK;
            case BLUE -> BORDER_BLUE;
        };
    }

    /**
     * 清除所有边框效果（用于场景切换等）
     */
    public static void clearAll() {
        activeBorders.clear();
    }
}