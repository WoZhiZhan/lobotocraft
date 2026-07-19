package com.wzz.lobotocraft.event.definition.mental_value;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.gui.overlay.ForgeGui;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MentalValueHud {
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final ResourceLocation GUI_ICONS_LOCATION = ResourceUtil.createInstance("textures/gui/spirit.png");

    // spirit.png布局：6个图标从左到右，每个32x32像素
    // 只使用：图标1（底图）、图标5（完整）、图标6（半）
    private static final int EMPTY_ICON_U = 6;      // 图标1: 底图/空图标
    private static final int FULL_ICON_U = 134;     // 图标5: 完整图标 (6 + 32*4)
    private static final int HALF_ICON_U = 166;     // 图标6: 半图标 (6 + 32*5)
    private static final int ICON_V = 4;            // 所有图标的V坐标

    @SubscribeEvent
    public static void onRenderGuiOverlayEvent(RenderGuiOverlayEvent.Pre event) {
        Player player = minecraft.player;
        if (player == null || player.isCreative() || player.isSpectator()) return;

        if (event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                ForgeGui gui = (ForgeGui) minecraft.gui;
                GuiGraphics guiGraphics = event.getGuiGraphics();
                RenderSystem.enableBlend();

                int screenWidth = event.getWindow().getGuiScaledWidth();
                int screenHeight = event.getWindow().getGuiScaledHeight();
                int left = screenWidth / 2 - 91;  // 生命值左侧对齐
                int top = screenHeight - gui.leftHeight;

                // 获取精神值
                float currentMental = mental.getMentalValue();
                float maxMental = mental.getEffectiveMaxMentalValue();

                // 转换为20点制（类似原版生命值系统）
                int mentalLevel = (int) Math.ceil((currentMental / maxMental) * 20);
                int full = mentalLevel / 2;       // 完整图标数量
                boolean half = mentalLevel % 2 != 0;  // 是否有半个图标

                // 渲染10个图标
                for (int i = 0; i < 10; i++) {
                    int iconX = left + i * 8;
                    guiGraphics.blit(GUI_ICONS_LOCATION, iconX, top,
                            (float) EMPTY_ICON_U / 4, (float) ICON_V / 4,
                            32 / 4, 32 / 4, 256 / 4, 256 / 4);
                    if (full > i) {
                        // 完整的精神值图标（图标5）
                        guiGraphics.blit(GUI_ICONS_LOCATION, iconX, top,
                                (float) FULL_ICON_U / 4, (float) ICON_V / 4,
                                32 / 4, 32 / 4, 256 / 4, 256 / 4);
                    } else if (full == i && half) {
                        // 半个精神值图标（图标6）
                        guiGraphics.blit(GUI_ICONS_LOCATION, iconX, top,
                                (float) HALF_ICON_U / 4, (float) ICON_V / 4,
                                32 / 4, 32 / 4, 256 / 4, 256 / 4);
                    }
                }

                gui.leftHeight += 10;

                // 如果精神值为0，显示闪烁警告效果
                if (currentMental <= 0 && (player.tickCount / 10) % 2 == 0) {
                    // 绘制闪烁效果（使用完整图标闪烁）
                    for (int i = 0; i < 10; i++) {
                        int iconX = left + i * 8;
                        guiGraphics.blit(GUI_ICONS_LOCATION, iconX, top,
                                (float) FULL_ICON_U / 4, (float) ICON_V / 4,
                                32 / 4, 32 / 4, 256 / 4, 256 / 4);
                    }
                }

                RenderSystem.disableBlend();
                event.setCanceled(true);
            });
        }
    }
}