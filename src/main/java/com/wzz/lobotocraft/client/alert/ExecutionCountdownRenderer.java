package com.wzz.lobotocraft.client.alert;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.entity.abnormality.EntityNothingThere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 一无所有伪装出逃时的10秒倒计时HUD。
 * 在屏幕中央显示红色倒计时数字，引导玩家找到并处决伪装文职。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ExecutionCountdownRenderer {
    private static final int TARGET_SCAN_INTERVAL_TICKS = 5;
    private static EntityNothingThere cachedTarget;
    private static int scanCooldown;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cachedTarget = null;
            scanCooldown = 0;
            return;
        }

        if (cachedTarget != null
                && !cachedTarget.isRemoved()
                && cachedTarget.level() == mc.level
                && cachedTarget.isEscapedDisguised()) {
            if (scanCooldown > 0) scanCooldown--;
            return;
        }

        cachedTarget = null;
        if (scanCooldown-- > 0) return;
        scanCooldown = TARGET_SCAN_INTERVAL_TICKS;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EntityNothingThere nt && nt.isEscapedDisguised()) {
                cachedTarget = nt;
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        EntityNothingThere target = cachedTarget;
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
        Component subtitle = Component.translatable(
                "overlay.lobotocraft.execution_countdown.subtitle");
        graphics.drawCenteredString(mc.font, subtitle, x + 1, y + 24, 0x80000000);
        graphics.drawCenteredString(mc.font, subtitle, x, y + 23, 0xFFFFAA00);

        RenderSystem.disableBlend();
    }
}
