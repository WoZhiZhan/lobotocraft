package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.init.ModParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GeburahMajorAttackEffects {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(
            new Vector3f(0.48F, 0.005F, 0.01F), 1.55F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(
            new Vector3f(1.0F, 0.025F, 0.015F), 1.05F);
    private static final DustParticleOptions SHADOW = new DustParticleOptions(
            new Vector3f(0.12F, 0.0F, 0.005F), 1.8F);

    private GeburahMajorAttackEffects() {
    }

    public static void spawnFrame(ServerLevel level, GeburahMajorMove move, int tick,
                                  Vec3 origin, Vec3 target, Vec3 forward) {
        if (move.bloodMist()) {
            spawnBloodMistFrame(level, move == GeburahMajorMove.EGO_BLOOD_MIST,
                    tick, target == null ? origin.add(forward.scale(3.0D)) : target);
        } else {
            spawnBodiesFrame(level, tick, origin, forward);
        }
    }

    public static void spawnImpact(ServerLevel level, GeburahMajorMove move,
                                   GeburahMajorMove.Cue cue, Vec3 origin,
                                   Vec3 target, Vec3 forward) {
        Vec3 axis = horizontal(forward);
        if (cue.hitKind() == GeburahMajorMove.HitKind.BOUND_TARGET) {
            Vec3 center = (target == null ? origin.add(axis.scale(3.0D)) : target)
                    .add(0.0D, 1.0D, 0.0D);
            int rays = cue.opening() ? 5 : cue.maxDamage() >= 30 ? 13
                    : cue.maxDamage() >= 12 ? 7 : 3;
            double radius = cue.maxDamage() >= 30 ? 2.8D
                    : cue.maxDamage() >= 12 ? 2.05D : 1.35D;
            radialBurst(level, center, rays, radius);
            return;
        }
        if (cue.hitKind() == GeburahMajorMove.HitKind.FORWARD_AREA) {
            forwardRift(level, origin, axis, 8.0D, 3.0D, true);
        } else {
            forwardRift(level, origin, axis, 12.0D, 15.0D, true);
        }
    }

    private static void spawnBloodMistFrame(ServerLevel level, boolean ego,
                                            int tick, Vec3 center) {
        int ribbons = ego ? 4 : 3;
        double baseRadius = 1.15D + Math.min(1.7D, tick * 0.014D);
        for (int ribbon = 0; ribbon < ribbons; ribbon++) {
            double phase = tick * (ego ? 0.42D : 0.34D)
                    + ribbon * Math.PI * 2.0D / ribbons;
            double radius = baseRadius + ribbon * 0.18D;
            double height = 0.3D + (tick * 0.095D + ribbon * 0.65D) % 2.55D;
            Vec3 point = center.add(Math.cos(phase) * radius, height,
                    Math.sin(phase) * radius);
            level.sendParticles(ribbon == 0 ? EDGE : BLOOD,
                    point.x, point.y, point.z, 1,
                    0.025D, 0.035D, 0.025D, 0.008D);
            Vec3 wake = point.add(-Math.sin(phase) * 0.55D, 0.08D,
                    Math.cos(phase) * 0.55D);
            level.sendParticles(SHADOW, wake.x, wake.y, wake.z,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        if (tick % 3 == 0) {
            level.sendParticles((SimpleParticleType) ModParticleTypes.RED.get(),
                    center.x, center.y + 1.2D, center.z, ego ? 3 : 2,
                    baseRadius * 0.42D, 0.75D, baseRadius * 0.42D, 0.03D);
        }
    }

    private static void spawnBodiesFrame(ServerLevel level, int tick,
                                         Vec3 origin, Vec3 forward) {
        Vec3 axis = horizontal(forward);
        Vec3 side = new Vec3(-axis.z, 0.0D, axis.x);
        double charge = Math.min(1.0D, tick / 46.0D);
        int echoes = tick < 46 ? 5 : 3;
        for (int i = 0; i < echoes; i++) {
            double lateral = (i - (echoes - 1) * 0.5D) * (1.4D + charge * 0.75D);
            double surge = 0.7D + ((tick * 0.48D + i * 2.1D) % 10.5D);
            Vec3 base = origin.add(axis.scale(surge)).add(side.scale(lateral));
            int vertical = tick < 46 ? 4 : 2;
            for (int y = 0; y < vertical; y++) {
                Vec3 point = base.add(0.0D, 0.2D + y * 0.62D, 0.0D);
                level.sendParticles((i + y) % 3 == 0 ? EDGE : BLOOD,
                        point.x, point.y, point.z, 1,
                        0.02D, 0.04D, 0.02D, 0.0D);
            }
        }
        if (tick % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 0.12D, origin.z,
                    5, 1.6D + charge * 2.4D, 0.08D,
                    1.6D + charge * 2.4D, 0.025D);
        }
    }

    private static void radialBurst(ServerLevel level, Vec3 center,
                                    int rays, double radius) {
        for (int ray = 0; ray < rays; ray++) {
            double angle = ray * Math.PI * 2.0D / rays + ray * 0.17D;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            for (int step = 1; step <= 7; step++) {
                double progress = step / 7.0D;
                Vec3 point = center.add(direction.scale(radius * progress))
                        .add(0.0D, (progress - 0.5D) * (ray % 2 == 0 ? 1.1D : -1.1D), 0.0D);
                level.sendParticles(step == 7 ? EDGE : BLOOD,
                        point.x, point.y, point.z, 1,
                        0.025D, 0.025D, 0.025D, 0.025D);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z,
                Math.max(3, rays / 2), radius * 0.2D, 0.35D,
                radius * 0.2D, 0.0D);
    }

    private static void forwardRift(ServerLevel level, Vec3 origin, Vec3 axis,
                                    double length, double width, boolean impact) {
        Vec3 side = new Vec3(-axis.z, 0.0D, axis.x);
        int forwardSamples = Math.max(18, (int) Math.ceil(length * 3.0D));
        int lateralRibbons = Math.max(3, (int) Math.ceil(width / 2.5D));
        for (int ribbon = 0; ribbon < lateralRibbons; ribbon++) {
            double lateral = lateralRibbons == 1 ? 0.0D
                    : -width * 0.5D + ribbon * width / (lateralRibbons - 1);
            for (int i = 0; i < forwardSamples; i++) {
                double progress = i / (double) (forwardSamples - 1);
                double height = 0.12D + Math.sin(progress * Math.PI) * 0.42D;
                Vec3 point = origin.add(axis.scale(length * progress))
                        .add(side.scale(lateral)).add(0.0D, height, 0.0D);
                level.sendParticles((i + ribbon) % 5 == 0 ? EDGE : BLOOD,
                        point.x, point.y, point.z, 1,
                        0.025D, 0.018D, 0.025D, impact ? 0.04D : 0.0D);
            }
        }
        Vec3 center = origin.add(axis.scale(length * 0.55D));
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.08D, center.z,
                Math.max(16, (int) (length + width)),
                width * 0.33D, 0.14D, length * 0.22D, 0.075D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                center.x, center.y + 1.0D, center.z,
                Math.max(6, lateralRibbons * 2), width * 0.22D,
                0.55D, length * 0.14D, 0.0D);
    }

    private static Vec3 horizontal(Vec3 direction) {
        Vec3 horizontal = direction.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }
}
