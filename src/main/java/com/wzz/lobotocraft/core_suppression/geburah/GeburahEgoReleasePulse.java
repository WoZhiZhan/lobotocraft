package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class GeburahEgoReleasePulse {
    public static final double RADIUS = 10.0D;

    private GeburahEgoReleasePulse() {
    }

    public static void trigger(ServerLevel level, EntityGeburah geburah) {
        Vec3 origin = geburah.position().add(0.0D, geburah.getBbHeight() * 0.45D, 0.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                geburah.getBoundingBox().inflate(RADIUS, 6.0D, RADIUS),
                player -> !player.isSpectator() && player.distanceToSqr(geburah) <= RADIUS * RADIUS)) {
            Vec3 impulse = outwardImpulse(player.position().subtract(geburah.position()));
            Vec3 retained = player.getDeltaMovement().multiply(0.15D, 0.25D, 0.15D);
            player.setDeltaMovement(retained.add(impulse));
            player.hurtMarked = true;
        }
        spawnAirflow(level, origin);
    }

    static Vec3 outwardImpulse(Vec3 displacement) {
        Vec3 horizontal = new Vec3(displacement.x, 0.0D, displacement.z);
        double distance = horizontal.length();
        if (distance < 1.0E-4D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        else horizontal = horizontal.scale(1.0D / distance);
        double falloff = 1.0D - Math.min(RADIUS, distance) / RADIUS;
        return horizontal.scale(1.25D + falloff * 0.75D).add(0.0D, 0.55D, 0.0D);
    }

    private static void spawnAirflow(ServerLevel level, Vec3 origin) {
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.9D + ring * 0.75D;
            double speed = 0.36D + ring * 0.10D;
            int samples = 28 + ring * 8;
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0D * i / samples;
                Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                Vec3 point = origin.add(direction.scale(radius))
                        .add(0.0D, (i & 1) * 0.42D - 0.18D, 0.0D);
                level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z,
                        0, direction.x, 0.08D + ring * 0.025D, direction.z, speed);
                if (ring == 0 && i % 2 == 0) {
                    level.sendParticles(ParticleTypes.POOF, point.x, point.y, point.z,
                            0, direction.x, 0.05D, direction.z, 0.28D);
                }
            }
        }
    }
}
