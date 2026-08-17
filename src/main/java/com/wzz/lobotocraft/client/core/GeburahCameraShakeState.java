package com.wzz.lobotocraft.client.core;

/** A short, deterministic and comfort-bounded camera impulse for Geburah attacks. */
public final class GeburahCameraShakeState {
    public static final int MAX_DURATION_TICKS = 24;
    public static final float MAX_AMPLITUDE = 2.0F;

    private static int totalTicks;
    private static int remainingTicks;
    private static float amplitude;
    private static float phase;

    private GeburahCameraShakeState() {
    }

    public static void trigger(int durationTicks, float requestedAmplitude, int seed) {
        if (!Float.isFinite(requestedAmplitude) || requestedAmplitude <= 0.0F) return;
        int safeDuration = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
        float safeAmplitude = Math.max(0.0F, Math.min(MAX_AMPLITUDE, requestedAmplitude));
        float current = amplitude * envelope(0.0F);
        if (safeAmplitude >= current) {
            totalTicks = safeDuration;
            remainingTicks = safeDuration;
            amplitude = safeAmplitude;
            phase = (seed & 0xFFFF) * 0.017453292F;
        } else {
            remainingTicks = Math.max(remainingTicks, safeDuration);
            totalTicks = Math.max(totalTicks, remainingTicks);
        }
    }

    public static void tick() {
        if (remainingTicks > 0) remainingTicks--;
        if (remainingTicks <= 0) clear();
    }

    public static Sample sample(float partialTick) {
        if (!isActive()) return Sample.ZERO;
        float elapsed = totalTicks - remainingTicks + Math.max(0.0F, partialTick);
        float strength = amplitude * envelope(partialTick);
        float yaw = (float) Math.sin(elapsed * 2.31F + phase) * strength;
        float pitch = (float) Math.sin(elapsed * 2.87F + phase * 0.73F)
                * strength * 0.62F;
        float roll = (float) Math.sin(elapsed * 1.73F + phase * 1.19F)
                * strength * 0.38F;
        return new Sample(yaw, pitch, roll);
    }

    private static float envelope(float partialTick) {
        if (totalTicks <= 0 || remainingTicks <= 0) return 0.0F;
        float remaining = Math.max(0.0F, remainingTicks - Math.max(0.0F, partialTick));
        float normalized = remaining / totalTicks;
        return normalized * normalized;
    }

    public static boolean isActive() {
        return remainingTicks > 0 && amplitude > 0.0F;
    }

    public static void clear() {
        totalTicks = 0;
        remainingTicks = 0;
        amplitude = 0.0F;
        phase = 0.0F;
    }

    static int remainingTicks() {
        return remainingTicks;
    }

    static float amplitude() {
        return amplitude;
    }

    public record Sample(float yaw, float pitch, float roll) {
        public static final Sample ZERO = new Sample(0.0F, 0.0F, 0.0F);
    }
}
