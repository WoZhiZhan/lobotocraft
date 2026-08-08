package com.wzz.lobotocraft.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShockwaveEffectManager {
    public static final int MAX_ACTIVE_SHOCKWAVES = 64;
    public static final float MAX_RADIUS = 128.0F;

    public static class Shockwave {
        public final double x;
        public final double y;
        public final double z;
        public final float maxRadius;
        public final int color;
        public final int ringCount = 3;
        public final float ringSpacing = 1.2F;
        public float currentRadius;
        public float alpha = 1.0F;
        private float previousRadius;
        private float previousAlpha = 1.0F;

        public Shockwave(double x, double y, double z, float maxRadius, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxRadius = maxRadius;
            this.color = color;
        }

        public boolean isExpired() {
            return alpha <= 0.0F;
        }

        public void tick() {
            previousRadius = currentRadius;
            previousAlpha = alpha;
            float speed = maxRadius / 30.0F;
            currentRadius = Math.min(maxRadius, currentRadius + speed);

            if (currentRadius >= maxRadius) {
                alpha = Math.max(0.0F, alpha - 0.05F);
            } else {
                alpha = 0.6F + 0.4F * (1.0F - currentRadius / maxRadius);
            }
        }

        public float getRenderRadius(float partialTick) {
            return lerp(clampPartialTick(partialTick), previousRadius, currentRadius);
        }

        public float getRenderAlpha(float partialTick) {
            return lerp(clampPartialTick(partialTick), previousAlpha, alpha);
        }
    }

    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();
    private static final List<Shockwave> SHOCKWAVES_VIEW = Collections.unmodifiableList(SHOCKWAVES);

    private ShockwaveEffectManager() {
    }

    public static void addShockwave(double x, double y, double z, float maxRadius, int color) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(maxRadius) || maxRadius <= 0.0F) {
            return;
        }
        while (SHOCKWAVES.size() >= MAX_ACTIVE_SHOCKWAVES) {
            SHOCKWAVES.remove(0);
        }
        SHOCKWAVES.add(new Shockwave(x, y, z, Math.min(maxRadius, MAX_RADIUS), color));
    }

    public static List<Shockwave> getShockwaves() {
        return SHOCKWAVES_VIEW;
    }

    public static int getActiveShockwaveCount() {
        return SHOCKWAVES.size();
    }

    public static void tick() {
        SHOCKWAVES.forEach(Shockwave::tick);
        SHOCKWAVES.removeIf(Shockwave::isExpired);
    }

    public static void clear() {
        SHOCKWAVES.clear();
    }

    private static float clampPartialTick(float partialTick) {
        return Math.max(0.0F, Math.min(1.0F, partialTick));
    }

    private static float lerp(float amount, float start, float end) {
        return start + amount * (end - start);
    }
}
