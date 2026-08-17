package com.wzz.lobotocraft.debug.prototype.queenstar;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class QueenStarPrototypeManager {
    static final int IMPACT_TICKS = 4;
    private static Shot active;

    private QueenStarPrototypeManager() {
    }

    static void start(Vec3 start, Vec3 end, int travelTicks) {
        if (!finite(start) || !finite(end) || start.distanceToSqr(end) < 0.01D) return;
        active = new Shot(start, end, Mth.clamp(travelTicks, 8, 80));
    }

    static void clear() {
        active = null;
    }

    static Shot getActive() {
        return active;
    }

    static void tick() {
        Shot shot = active;
        if (shot == null) return;
        shot.ageTicks++;
        if (shot.ageTicks >= shot.travelTicks + IMPACT_TICKS) {
            active = null;
        }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static final class Shot {
        final Vec3 start;
        final Vec3 end;
        final Vec3 direction;
        final int travelTicks;
        int ageTicks;

        Shot(Vec3 start, Vec3 end, int travelTicks) {
            this.start = start;
            this.end = end;
            this.direction = end.subtract(start).normalize();
            this.travelTicks = travelTicks;
        }

        float travelProgress(float partialTick) {
            return Mth.clamp((ageTicks + partialTick) / travelTicks, 0.0F, 1.0F);
        }

        float impactProgress(float partialTick) {
            return Mth.clamp((ageTicks + partialTick - travelTicks) / IMPACT_TICKS, 0.0F, 1.0F);
        }

        Vec3 position(float partialTick) {
            return start.lerp(end, travelProgress(partialTick));
        }
    }
}
