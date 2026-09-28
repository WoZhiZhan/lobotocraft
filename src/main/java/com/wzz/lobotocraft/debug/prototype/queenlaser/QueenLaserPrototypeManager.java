package com.wzz.lobotocraft.debug.prototype.queenlaser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class QueenLaserPrototypeManager {
    private static Beam active;
    private static float exposure;

    private QueenLaserPrototypeManager() {
    }

    public static void start(Vec3 start, Vec3 end, int mode, int castTicks, int durationTicks) {
        start(start, end, mode, castTicks, durationTicks, 0);
    }

    public static void start(Vec3 start, Vec3 end, int mode, int castTicks, int durationTicks,
                             int ageTicks) {
        if (!finite(start) || !finite(end) || start.distanceToSqr(end) < 0.01D) return;
        int safeMode = mode == QueenLaserPrototypePacket.MODE_MAX
                ? QueenLaserPrototypePacket.MODE_MAX : QueenLaserPrototypePacket.MODE_PREVIEW;
        int safeDuration = Mth.clamp(durationTicks, 20, 20 * 30);
        int safeCastTicks = Mth.clamp(castTicks, 1, Math.max(1, safeDuration - 1));
        active = new Beam(start, end, safeMode, safeCastTicks, safeDuration);
        active.ageTicks = Mth.clamp(ageTicks, 0, Math.max(0, safeDuration - 1));
    }

    public static void clear() {
        active = null;
        exposure = 0.0F;
    }

    static Beam getActive() {
        return active;
    }

    static float getExposure() {
        return exposure;
    }

    static void tick() {
        Beam beam = active;
        if (beam == null) {
            exposure *= 0.90F;
            if (exposure < 0.001F) exposure = 0.0F;
            return;
        }
        beam.ageTicks++;

        spawnParticles(beam);
        updateExposure(beam);
        if (beam.ageTicks >= beam.durationTicks) {
            active = null;
        }
    }

    private static void spawnParticles(Beam beam) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        float castProgress = beam.castProgress(0.0F);
        float fireProgress = beam.fireProgress(0.0F);
        Vec3 axis = beam.end.subtract(beam.start);
        double length = axis.length();
        Vec3 direction = axis.scale(1.0D / length);
        Basis basis = basis(direction);
        int count = beam.isMax() ? 8 : 3;

        if (castProgress < 1.0F) {
            for (int i = 0; i < count * 2; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                double radius = (beam.isMax() ? 3.7D : 2.4D)
                        * (0.55D + level.random.nextDouble() * 0.45D);
                Vec3 radial = basis.side.scale(Math.cos(angle) * radius)
                        .add(basis.up.scale(Math.sin(angle) * radius));
                Vec3 point = beam.start.add(radial).subtract(direction.scale(0.2D));
                Vec3 inward = radial.normalize().scale(-0.045D);
                level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH,
                        point.x, point.y, point.z, inward.x, inward.y, inward.z);
            }
            return;
        }

        for (int i = 0; i < count; i++) {
            double along = level.random.nextDouble();
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double radius = (beam.isMax() ? 1.9D : 1.0D) * (0.2D + level.random.nextDouble() * 0.8D);
            Vec3 radial = basis.side.scale(Math.cos(angle) * radius)
                    .add(basis.up.scale(Math.sin(angle) * radius));
            Vec3 point = beam.start.add(direction.scale(length * along)).add(radial);
            Vec3 drift = direction.scale(0.08D + level.random.nextDouble() * 0.12D);
            level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z, drift.x, drift.y, drift.z);
        }

        if (fireProgress > 0.0F) {
            int impactCount = beam.isMax() ? 10 : 4;
            for (int i = 0; i < impactCount; i++) {
                Vec3 velocity = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(),
                        level.random.nextGaussian()).normalize().scale(0.08D + level.random.nextDouble() * 0.18D);
                level.addParticle(i % 2 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.WITCH,
                        beam.end.x, beam.end.y, beam.end.z, velocity.x, velocity.y, velocity.z);
            }
        }
    }

    private static void updateExposure(Beam beam) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            exposure *= 0.90F;
            return;
        }

        float fireProgress = beam.fireProgress(0.0F);
        float firing = QueenLaserTimeline.firingAlpha(fireProgress);
        if (firing <= 0.001F) {
            exposure += (0.0F - exposure) * 0.16F;
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;
        Vec3 cameraPosition = camera.getPosition();
        org.joml.Vector3f lookVector = camera.getLookVector();
        Vec3 cameraLook = new Vec3(lookVector.x, lookVector.y, lookVector.z);
        int samples = beam.isMax() ? 18 : 12;
        float secondStage = QueenLaserTimeline.secondStage(fireProgress);
        float width = (beam.isMax() ? 2.65F : 1.55F) * Mth.lerp(secondStage, 0.82F, 1.18F);
        Vec3 axis = beam.end.subtract(beam.start);

        float visibility = QueenLaserVisibility.measure(cameraPosition, cameraLook,
                beam.start, beam.end, width, samples, index -> {
                    double along = (index + 0.5D) / samples;
                    Vec3 sample = beam.start.add(axis.scale(along));
                    HitResult hit = level.clip(new ClipContext(cameraPosition, sample,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
                    return hit.getType() == HitResult.Type.MISS
                            || hit.getLocation().distanceToSqr(sample) <= width * width * 0.20D;
                });
        float visibleAmount = visibility <= 0.035F
                ? 0.0F : (visibility - 0.035F) / 0.965F;
        float target = visibleAmount * firing * (beam.isMax() ? 1.0F : 0.72F);
        float response = target > exposure ? 0.09F : 0.045F;
        exposure += (target - exposure) * response;
        exposure = Mth.clamp(exposure, 0.0F, 1.0F);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static Basis basis(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = direction.cross(reference).normalize();
        return new Basis(side, side.cross(direction).normalize());
    }

    static final class Beam {
        final Vec3 start;
        final Vec3 end;
        final int mode;
        final int castTicks;
        final int durationTicks;
        int ageTicks;

        Beam(Vec3 start, Vec3 end, int mode, int castTicks, int durationTicks) {
            this.start = start;
            this.end = end;
            this.mode = mode;
            this.castTicks = castTicks;
            this.durationTicks = durationTicks;
        }

        boolean isMax() {
            return mode == QueenLaserPrototypePacket.MODE_MAX;
        }

        float progress(float partialTick) {
            return Mth.clamp((ageTicks + partialTick) / durationTicks, 0.0F, 1.0F);
        }

        float castProgress(float partialTick) {
            return QueenLaserTimeline.castProgress(ageTicks + partialTick, castTicks);
        }

        float fireProgress(float partialTick) {
            return QueenLaserTimeline.fireProgress(ageTicks + partialTick, castTicks, durationTicks);
        }
    }

    record Basis(Vec3 side, Vec3 up) {
    }
}
