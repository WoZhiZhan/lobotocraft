package com.wzz.lobotocraft.debug.prototype.queenlaser;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntPredicate;

/** Pure visibility weighting used by the prototype exposure simulation. */
final class QueenLaserVisibility {
    private QueenLaserVisibility() {
    }

    static float measure(Vec3 cameraPosition, Vec3 cameraLook, Vec3 start, Vec3 end,
                         float beamWidth, int sampleCount, IntPredicate sampleVisible) {
        if (sampleCount <= 0 || beamWidth <= 0.0F) return 0.0F;
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 1.0E-6D) return 0.0F;

        Vec3 look = cameraLook.normalize();
        double possibleWeight = 0.0D;
        double visibleWeight = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            double along = (i + 0.5D) / sampleCount;
            Vec3 sample = start.add(axis.scale(along));
            Vec3 toSample = sample.subtract(cameraPosition);
            double distance = toSample.length();
            if (distance < 0.01D) continue;

            double facing = look.dot(toSample.scale(1.0D / distance));
            float inView = smoothstep(-0.08F, 0.32F, (float) facing);
            float angularSize = Mth.clamp((float) (beamWidth / Math.max(0.5D, distance) * 7.0D),
                    0.0F, 1.0F);
            double weight = inView * angularSize;
            possibleWeight += weight;
            if (weight > 0.0D && sampleVisible.test(i)) visibleWeight += weight;
        }

        if (possibleWeight < 1.0E-5D || visibleWeight < 1.0E-5D) return 0.0F;
        float visibleRatio = (float) (visibleWeight / possibleWeight);
        float screenCoverage = 1.0F - (float) Math.exp(-visibleWeight * 0.20D);
        return Mth.clamp(visibleRatio * screenCoverage, 0.0F, 1.0F);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
