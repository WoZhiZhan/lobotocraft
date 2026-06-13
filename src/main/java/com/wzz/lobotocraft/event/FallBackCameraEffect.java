package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FallBackCameraEffect {

    private static final Map<UUID, FallBackState> playerStates = new HashMap<>();

    // 总动画持续时间（tick）- 包含摔倒和恢复
    private static final int TOTAL_DURATION = 40; // 2秒总时长

    // 摔倒阶段持续时间
    private static final int FALL_DURATION = 15; // 前0.75秒摔倒

    // 恢复阶段持续时间
    private static final int RECOVER_DURATION = 25; // 后1.25秒恢复

    // 摄像机最大俯仰角度（向上看）
    private static final float MAX_PITCH = -35.0f;

    // 摄像机翻滚角度
    private static final float MAX_ROLL = 15.0f;

    // 偏航摆动幅度
    private static final float MAX_YAW_SHAKE = 5.0f;

    public static void triggerFallBack(Player player) {
        if (player.level().isClientSide) {
            playerStates.put(player.getUUID(), new FallBackState());
        }
    }

    public static void cancelFallBack(Player player) {
        playerStates.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) return;

        FallBackState state = playerStates.get(player.getUUID());
        if (state != null) {
            state.tick++;

            // 动画完成后移除状态
            if (state.tick > TOTAL_DURATION) {
                playerStates.remove(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) return;

        FallBackState state = playerStates.get(player.getUUID());
        if (state != null) {
            // 使用partialTick获得更平滑的动画（在tick之间插值）
            float currentTick = (float) (state.tick + event.getPartialTick());

            float intensity; // 动画强度（0到1再到0）

            if (currentTick < FALL_DURATION) {
                // 摔倒阶段：0 -> 1
                float fallProgress = currentTick / FALL_DURATION;
                intensity = easeOutCubic(fallProgress);
            } else {
                // 恢复阶段：1 -> 0
                float recoverProgress = (currentTick - FALL_DURATION) / RECOVER_DURATION;
                intensity = 1.0f - easeInOutCubic(recoverProgress);
            }

            // 限制intensity范围
            intensity = Math.max(0.0f, Math.min(1.0f, intensity));

            // 计算俯仰角（向上看）
            float pitchOffset = MAX_PITCH * intensity;

            // 计算翻滚角（使用正弦函数产生更自然的侧倾）
            float rollProgress = (float) Math.sin(intensity * Math.PI);
            float rollOffset = MAX_ROLL * rollProgress;

            // 计算偏航摆动（摔倒时有，恢复时减弱）
            float shakeIntensity = intensity * (1.0f - (currentTick / TOTAL_DURATION) * 0.5f);
            float yawOffset = (float) (Math.sin(currentTick * 0.6) * MAX_YAW_SHAKE * shakeIntensity);

            // 应用摄像机变换
            event.setPitch(event.getPitch() + pitchOffset);
            event.setRoll(event.getRoll() + rollOffset);
            event.setYaw(event.getYaw() + yawOffset);
        }
    }

    /**
     * 三次方缓出 - 用于摔倒阶段（快速开始，缓慢结束）
     */
    private static float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    /**
     * 三次方缓入缓出 - 用于恢复阶段（平滑过渡）
     */
    private static float easeInOutCubic(float x) {
        return x < 0.5f
                ? 4 * x * x * x
                : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    /**
     * 摔倒状态类
     */
    private static class FallBackState {
        int tick = 0;
    }
}