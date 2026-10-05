package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded cosmetic layout, shared by delivery and client rendering; never changes combat. */
public final class GeburahGroundImpact {
    public static final int MAX_SHARDS = 20;
    public static final int MAX_ACTIVE_SHARDS = 64;
    public static final int MAX_LIFETIME = 22;
    public static final double VIEW_DISTANCE = 48.0D;
    public static final double GRAVITY = 0.09D;

    private GeburahGroundImpact() {
    }

    public static boolean enabled(GeburahMove move, boolean egoMove, boolean egoManifested) {
        return egoMove && egoManifested && move == GeburahMove.VERTICAL_SLASH;
    }

    /** particleQuality: 0=all, 1=decreased, 2=minimal. Distance applies an additional LOD. */
    public static int count(int particleQuality, double distanceSquared, int active) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D
                || distanceSquared > VIEW_DISTANCE * VIEW_DISTANCE) return 0;
        int desired = particleQuality <= 0 ? MAX_SHARDS : particleQuality == 1 ? 10 : 4;
        if (distanceSquared > 24.0D * 24.0D) desired = Math.max(2, desired / 2);
        return Math.min(desired, Math.max(0, MAX_ACTIVE_SHARDS - Math.max(0, active)));
    }

    public static List<Shard> layout(int seed, int count, double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) return List.of();
        radius = Math.min(radius, 3.0D);
        Random random = new Random(seed);
        List<Shard> shards = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_SHARDS, Math.max(0, count)); i++) {
            boolean core = i < 4;
            double angle = i * 2.399963229728653D + random.nextDouble() * 0.3D;
            double radial = radius * (core ? 0.15D + random.nextDouble() * 0.2D
                    : 0.55D + random.nextDouble() * 0.4D);
            double lift = core ? 0.4D + random.nextDouble() * 0.06D
                    : 0.3D + random.nextDouble() * 0.08D;
            shards.add(new Shard(Math.cos(angle) * radial, Math.sin(angle) * radial,
                    lift, core ? 0.72F + random.nextFloat() * 0.16F
                            : 0.46F + random.nextFloat() * 0.18F,
                    core ? 0 : 2 + random.nextInt(2),
                    (int) Math.ceil(2.0D * lift / GRAVITY) + 7,
                    (random.nextFloat() - 0.5F) * 0.25F));
        }
        return List.copyOf(shards);
    }

    public record Shard(double offsetX, double offsetZ, double velocityY, float size,
                        int delay, int duration, float tilt) {
        public int lifetime() {
            return delay + duration;
        }

        public double height(double ticks) {
            return height(ticks, Double.POSITIVE_INFINITY);
        }

        private double launchVelocity(double clearance) {
            return Math.min(velocityY, Math.sqrt(2.0D * GRAVITY * Math.max(0.0D, clearance)));
        }

        public double flightTime(double clearance) {
            return 2.0D * launchVelocity(clearance) / GRAVITY;
        }

        public double height(double ticks, double clearance) {
            if (ticks <= 0) return 0;
            return Math.max(0.0D, launchVelocity(clearance) * ticks
                    - 0.5D * GRAVITY * ticks * ticks);
        }

        public float opacity(double ticks, double clearance) {
            if (ticks < 0 || ticks >= duration) return 0.0F;
            double fadeStart = Math.min(flightTime(clearance) + 2.0D, duration - 5.0D);
            return (float) Math.max(0.0D, Math.min(1.0D, 1.0D - (ticks - fadeStart) / 5.0D));
        }

        public float tiltAt(double ticks, double clearance) {
            double flight = flightTime(clearance);
            if (ticks <= 0 || flight <= 0 || ticks >= flight) return 0;
            return (float) (Math.sin(ticks / flight * Math.PI) * tilt);
        }
    }
}
