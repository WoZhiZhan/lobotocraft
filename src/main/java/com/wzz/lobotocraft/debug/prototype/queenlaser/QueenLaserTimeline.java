package com.wzz.lobotocraft.debug.prototype.queenlaser;

import net.minecraft.util.Mth;

/** Source-derived timing for the throwaway Queen of Hatred laser prototype. */
final class QueenLaserTimeline {
    static final int CAST_TICKS = 134; // Original 13.4 s casting animation at 0.5 duration.
    private static final int PREVIEW_FIRE_TICKS = 120;
    private static final int MAX_FIRE_TICKS = 200;
    private static final float[] LAYER_SCALE_START = {0.5099F, 0.5174F, 0.5224F, 0.5299F};
    private static final float[] LAYER_SCALE_END = {0.5224F, 0.5274F, 0.5348F, 0.5448F};

    private QueenLaserTimeline() {
    }

    static int totalTicks(boolean maximum) {
        return CAST_TICKS + (maximum ? MAX_FIRE_TICKS : PREVIEW_FIRE_TICKS);
    }

    static float castProgress(float ageTicks) {
        return castProgress(ageTicks, CAST_TICKS);
    }

    static float fireProgress(float ageTicks, int durationTicks) {
        return fireProgress(ageTicks, CAST_TICKS, durationTicks);
    }

    static float castProgress(float ageTicks, int castTicks) {
        return Mth.clamp(ageTicks / Math.max(1, castTicks), 0.0F, 1.0F);
    }

    static float fireProgress(float ageTicks, int castTicks, int durationTicks) {
        int safeCastTicks = Math.max(1, castTicks);
        int fireTicks = Math.max(1, durationTicks - safeCastTicks);
        return Mth.clamp((ageTicks - safeCastTicks) / fireTicks, 0.0F, 1.0F);
    }

    static float glitterAlpha(float castProgress, float fireProgress) {
        return smoothstep(0.0F, 0.035F, castProgress) * endEnvelope(fireProgress);
    }

    static float razorAlpha(float castProgress, float fireProgress) {
        return smoothstep(0.353F, 0.405F, castProgress) * endEnvelope(fireProgress);
    }

    static float mainCircleAlpha(float castProgress, float fireProgress) {
        return smoothstep(0.510F, 0.550F, castProgress) * endEnvelope(fireProgress);
    }

    static float mainCircleLayerScale(float castProgress, int layer) {
        int index = Mth.clamp(layer, 0, LAYER_SCALE_START.length - 1);
        return smoothstep(LAYER_SCALE_START[index], LAYER_SCALE_END[index], castProgress);
    }

    static float coloredRingAlpha(float castProgress, float fireProgress) {
        return smoothstep(0.761F, 0.805F, castProgress) * endEnvelope(fireProgress);
    }

    static float pillarAlpha(float castProgress, float fireProgress) {
        float appear = smoothstep(0.895F, 0.995F, castProgress);
        float extinguish = 1.0F - smoothstep(0.02F, 0.12F, fireProgress);
        return appear * extinguish;
    }

    static float firingAlpha(float fireProgress) {
        return smoothstep(0.0F, 0.08F, fireProgress) * endEnvelope(fireProgress);
    }

    static float secondStage(float fireProgress) {
        return smoothstep(0.45F, 0.56F, fireProgress);
    }

    static float endEnvelope(float fireProgress) {
        return 1.0F - smoothstep(0.90F, 0.97F, fireProgress);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
