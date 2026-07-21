package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ShaderQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ResolutionShaderHandler {

    private static ShaderQuality current = ShaderQuality.NATIVE;
    private static int remainingTicks = 0;      // 剩余持续tick数，0表示永久

    /**
     * 应用画质（永久）
     */
    public static void apply(ShaderQuality quality) {
        apply(quality, 0);
    }

    /**
     * 应用画质，持续指定时间后自动恢复NATIVE
     * @param quality 目标画质
     * @param durationTicks 持续时间（tick），<=0 表示永久
     */
    public static void apply(ShaderQuality quality, int durationTicks) {
        current = quality;
        remainingTicks = durationTicks;

        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.shutdownEffect();
        if (quality.effect != null) {
            mc.gameRenderer.loadEffect(quality.effect);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                apply(ShaderQuality.NATIVE);
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("画质已恢复"), true);
                }
                return;
            }
        }

        // 重载检查（防止着色器丢失）
        if (current.effect == null) return;
        if (mc.gameRenderer.currentEffect() == null) {
            mc.gameRenderer.loadEffect(current.effect);
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 登录时如果有计时任务，保留状态继续计时
        if (current != ShaderQuality.NATIVE) {
            // 重设着色器，保留剩余时间
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.shutdownEffect();
            if (current.effect != null) {
                mc.gameRenderer.loadEffect(current.effect);
            }
        }
    }
}