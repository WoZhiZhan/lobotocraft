package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.GeburahGroundImpactPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GeburahAttackEffects {
    private static final DustParticleOptions DEEP_RED = new DustParticleOptions(
            new Vector3f(0.42F, 0.01F, 0.015F), 1.35F);
    private static final DustParticleOptions BRIGHT_RED = new DustParticleOptions(
            new Vector3f(1.0F, 0.035F, 0.025F), 1.05F);

    private GeburahAttackEffects() {
    }

    public static void spawn(ServerLevel level, GeburahMove move, boolean ego, boolean egoManifested,
                             Vec3 origin, Vec3 forward) {
        Vec3 axis = horizontalDirection(forward);
        GeburahHitbox.Profile profile = GeburahHitbox.profile(move, ego);
        switch (move) {
            case SLASH -> {
                level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 0.15D, origin.z,
                        ego ? 10 : 7, 0.45D, 0.08D, 0.45D, 0.018D);
            }
            case STAB -> {
                Vec3 tip = origin.add(axis.scale(profile.reach())).add(0.0D, 1.35D, 0.0D);
                level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z,
                        ego ? 14 : 10, 0.32D, 0.28D, 0.32D, 0.08D);
                level.sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z,
                        ego ? 7 : 5, 0.25D, 0.18D, 0.25D, 0.025D);
            }
            case VERTICAL_SLASH -> {
                Vec3 impact = origin.add(axis.scale(profile.reach() * 0.72D));
                level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 0.12D, impact.z,
                        ego ? 16 : 11, profile.halfWidth() * 0.55D,
                        0.10D, profile.halfWidth() * 0.55D, 0.035D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        impact.x, impact.y + 0.18D, impact.z,
                        ego ? 12 : 8, profile.halfWidth() * 0.48D,
                        0.08D, profile.halfWidth() * 0.48D, 0.025D);
                boolean volumetric = GeburahGroundImpact.enabled(move, ego, egoManifested);
                spawnGroundDebris(level, impact, profile.halfWidth(), ego, volumetric);
                if (volumetric) {
                    GeburahGroundImpactPacket packet = new GeburahGroundImpactPacket(
                            level.dimension().location(), impact,
                            (float) profile.halfWidth(), level.random.nextInt());
                    for (var player : level.players()) {
                        if (player.position().distanceToSqr(impact)
                                <= GeburahGroundImpact.VIEW_DISTANCE * GeburahGroundImpact.VIEW_DISTANCE) {
                            MessageLoader.getLoader().sendToPlayer(player, packet);
                        }
                    }
                }
            }
        }
    }

    private static void spawnGroundDebris(ServerLevel level, Vec3 impact,
                                          double radius, boolean ego, boolean volumetric) {
        BlockPos sample = BlockPos.containing(impact.x, impact.y + 0.2D, impact.z);
        BlockState state = level.getBlockState(sample);
        for (int depth = 0; depth < 3 && state.isAir(); depth++) {
            sample = sample.below();
            state = level.getBlockState(sample);
        }
        if (state.isAir()) return;
        level.playSound(null, sample, state.getSoundType().getBreakSound(),
                SoundSource.HOSTILE, ego ? 1.65F : 1.28F, ego ? 0.52F : 0.62F);
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
        level.sendParticles(debris, impact.x, sample.getY() + 1.08D, impact.z,
                volumetric ? 18 : ego ? 54 : 36, radius * 0.82D, 0.12D, radius * 0.82D,
                ego ? 0.34D : 0.25D);
        level.sendParticles(ParticleTypes.POOF, impact.x, sample.getY() + 1.04D, impact.z,
                ego ? 18 : 12, radius * 0.68D, 0.08D, radius * 0.68D, 0.045D);
    }

    public static void spawnPursuitDash(ServerLevel level, Vec3 start, Vec3 end,
                                        boolean ego) {
        Vec3 axis = end.subtract(start);
        int samples = Math.max(8, (int) Math.ceil(axis.length() * 8.0D));
        for (int i = 0; i < samples; i++) {
            double progress = i / (double) Math.max(1, samples - 1);
            Vec3 base = start.lerp(end, progress);
            for (int layer = 0; layer < (ego ? 3 : 2); layer++) {
                Vec3 point = base.add(0.0D, 0.35D + layer * 0.62D, 0.0D);
                level.sendParticles(layer == 0 ? BRIGHT_RED : DEEP_RED,
                        point.x, point.y, point.z, 1,
                        0.02D, 0.035D, 0.02D, 0.0D);
            }
        }
        Vec3 endCenter = end.add(0.0D, 1.1D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, endCenter.x, endCenter.y, endCenter.z,
                ego ? 7 : 4, 0.22D, 0.45D, 0.22D, 0.025D);
    }

    private static Vec3 horizontalDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }
}
