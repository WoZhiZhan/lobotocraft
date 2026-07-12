package com.wzz.lobotocraft.client.alert;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.entity.abnormality.EntityNothingThere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 一无所有伪装出逃时的10秒倒计时HUD。
 * 在屏幕中央显示红色倒计时数字，引导玩家找到并处决伪装文职。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ExecutionCountdownRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 查找附近处于伪装出逃状态的一无所有
        EntityNothingThere target = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EntityNothingThere nt && nt.isEscapedDisguised()) {
                target = nt;
                break;
            }
        }
        if (target == null) return;

        int countdown = target.getExecutionCountdownSeconds();
        if (countdown <= 0) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // 屏幕中央偏上
        int x = screenW / 2;
        int y = screenH / 2 - 40;

        // 倒计时数字
        String text = String.valueOf(countdown);
        int color = countdown <= 3 ? 0xFFFF0000 : 0xFFFF4444; // 最后3秒变红

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 阴影 + 主文字
        graphics.drawCenteredString(mc.font, text, x + 1, y + 1, 0x80000000);
        graphics.drawCenteredString(mc.font, text, x, y, color);

        // 副标题
        String subtitle = "找到并处决伪装的文职！";
        graphics.drawCenteredString(mc.font, subtitle, x + 1, y + 24, 0x80000000);
        graphics.drawCenteredString(mc.font, subtitle, x, y + 23, 0xFFFFAA00);

        RenderSystem.disableBlend();
    }
}
