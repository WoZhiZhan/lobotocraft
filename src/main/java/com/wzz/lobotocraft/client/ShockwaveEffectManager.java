package com.wzz.lobotocraft.client;

import java.util.ArrayList;
import java.util.List;

public class ShockwaveEffectManager {

    public static class Shockwave {
        public final double x, y, z;
        public final float maxRadius;
        public final int color;
        public float currentRadius = 0f;
        public float alpha = 1.0f;

        // 参数：圆环层数（叠加多圈增加厚度感）
        public final int ringCount = 3;
        public final float ringSpacing = 1.2f; // 每圈间距

        public Shockwave(double x, double y, double z, float maxRadius, int color) {
            this.x = x; this.y = y; this.z = z;
            this.maxRadius = maxRadius;
            this.color = color;
        }

        public boolean isExpired() { return alpha <= 0f; }

        public void tick(float partialTick) {
            float speed = maxRadius / 30f; // 30tick 扩展到最大
            currentRadius += speed * partialTick;

            // 超过最大半径后开始淡出
            if (currentRadius >= maxRadius) {
                alpha = Math.max(0f, alpha - 0.05f * partialTick);
            } else {
                // 扩展期间轻微透明度变化
                alpha = 0.6f + 0.4f * (1f - currentRadius / maxRadius);
            }
        }
    }

    private static final List<Shockwave> shockwaves = new ArrayList<>();

    public static void addShockwave(double x, double y, double z, float maxRadius, int color) {
        shockwaves.add(new Shockwave(x, y, z, maxRadius, color));
    }

    public static List<Shockwave> getShockwaves() { return shockwaves; }

    public static void tick() {
        shockwaves.removeIf(Shockwave::isExpired);
    }
}