package com.wzz.lobotocraft.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WorkProgressHUD {

    private static final ResourceLocation ARMOR_LOCK_TEXTURE = 
            ResourceUtil.createInstance("textures/gui/armor_lock.png");

    private static final int ICON_WIDTH = 29;
    private static final int ICON_HEIGHT = 40;
    
    // 显示位置
    private static final int HUD_X = 12;  // 距离屏幕左边的距离
    private static final int HUD_Y = 10;  // 距离屏幕顶部的距离

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        
        if (player == null) {
            return;
        }

        // 只在公司维度显示
        if (player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            return;
        }

        // 调试模式或F3界面打开时不显示
        if (mc.options.renderDebug) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            int x = HUD_X;
            int y = HUD_Y;

            // 始终显示天数（在最上方）
            String dayText = "§6第 " + data.getCurrentDay() + " 天";

            // 绘制天数文字
            guiGraphics.drawString(mc.font, dayText, x, y, 0xFFFFFF);

            guiGraphics.drawString(mc.font, "§6工作次数：" + data.getTodayWorkCount(), x, y + 10, 0xFFFFFF);

            // 如果装备已锁定，显示装备锁定图标
            if (data.isArmorLocked()) {
                // 图标显示在天数下方
                int iconX = x;
                int iconY = y + 25;

                // 启用混合和透明度
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                
                // 绘制装备锁定图标
                guiGraphics.blit(
                        ARMOR_LOCK_TEXTURE,
                        iconX, iconY,              // 屏幕位置 (x, y)
                        0, 0,                      // 纹理UV起始位置
                        ICON_WIDTH, ICON_HEIGHT,   // 绘制尺寸
                        ICON_WIDTH, ICON_HEIGHT    // 纹理总尺寸
                );
                
                RenderSystem.disableBlend();
            }
        });
    }
}