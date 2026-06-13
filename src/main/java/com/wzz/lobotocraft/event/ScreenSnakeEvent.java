package com.wzz.lobotocraft.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.NoSuchElementException;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ScreenSnakeEvent {

    private static int shakeTicks = 0;
    private static final int MAX_SHAKE_TICKS = 60;
    private static final float DEFAULT_SHAKE_INTENSITY = 10.0F;
    private static float shakeIntensity = DEFAULT_SHAKE_INTENSITY;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (shakeTicks > 0) {
                shakeTicks--;
            }
        }
    }

    public static void triggerShake(int ticks) {
        triggerShake(ticks, DEFAULT_SHAKE_INTENSITY);
    }

    /** 触发屏幕扭曲并指定强度(原先为固定值,现改为可传入参数) */
    public static void triggerShake(int ticks, float intensity) {
        shakeTicks = Math.min(ticks, MAX_SHAKE_TICKS);
        shakeIntensity = intensity;
    }

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Pre event) {
        if (shakeTicks > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                float shakeStrength = (float) shakeTicks / MAX_SHAKE_TICKS;
                float intensity = shakeIntensity * shakeStrength;

                float shakeX = (mc.level.random.nextFloat() - 0.5F) * intensity;
                float shakeY = (mc.level.random.nextFloat() - 0.5F) * intensity;
                try {
                    event.getGuiGraphics().pose().pushPose();
                    event.getGuiGraphics().pose().translate(shakeX, shakeY, 0);
                } catch (NoSuchElementException ignored) {
                }
            }
        }
    }

    @SubscribeEvent
    public static void onGuiScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (shakeTicks > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                float shakeStrength = (float) shakeTicks / MAX_SHAKE_TICKS;
                float intensity = shakeIntensity * shakeStrength;

                float shakeX = (mc.level.random.nextFloat() - 0.5F) * intensity;
                float shakeY = (mc.level.random.nextFloat() - 0.5F) * intensity;

                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(shakeX, shakeY, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onGuiScreenRenderPost(ScreenEvent.Render.Post event) {
        if (shakeTicks > 0) {
            event.getGuiGraphics().pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onRenderGameOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (shakeTicks > 0) {
            event.getGuiGraphics().pose().popPose();
        }
    }
}