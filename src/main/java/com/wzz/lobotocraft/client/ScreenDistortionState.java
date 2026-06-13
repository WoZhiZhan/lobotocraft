package com.wzz.lobotocraft.client;

public class ScreenDistortionState {
    private static float targetIntensity = 0f;   // 目标强度
    private static float currentIntensity = 0f;  // 当前实际强度（平滑过渡）
    private static int remainingTicks = 0;

    private static final int FADE_IN_TICKS = 40;  // 淡入 2秒
    private static final int FADE_OUT_TICKS = 60; // 淡出 3秒
    private static int totalDuration = 0;
    private static final float FADE_SPEED = 0.02f; // 每tick变化量

    public static void activate(float intensity, int durationTicks) {
        targetIntensity = intensity;
        remainingTicks = durationTicks;
        totalDuration = durationTicks;
    }

    public static void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;

            // 淡入阶段
            if (totalDuration - remainingTicks < FADE_IN_TICKS) {
                currentIntensity = Math.min(currentIntensity + FADE_SPEED, targetIntensity);
            }
            // 淡出阶段
            else if (remainingTicks < FADE_OUT_TICKS) {
                currentIntensity = Math.max(currentIntensity - FADE_SPEED, 0f);
            }
            // 中间稳定阶段（加一点随机抖动，更像热浪）
            else {
                float jitter = (float)(Math.sin(System.currentTimeMillis() * 0.003) * 0.05);
                currentIntensity = targetIntensity + jitter;
            }
        } else {
            // 结束后继续淡出直到归零
            currentIntensity = Math.max(currentIntensity - FADE_SPEED, 0f);
        }
    }

    public static boolean isActive() {
        return remainingTicks > 0 || currentIntensity > 0.001f;
    }

    public static float getCurrentIntensity() {
        return currentIntensity;
    }
}