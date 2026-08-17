package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Layers short vanilla transients under Geburah's authored attack audio. */
public final class GeburahSoundscape {
    private GeburahSoundscape() {
    }

    public static void playAttackMotion(Level level, BlockPos origin,
                                        GeburahMove move, boolean ego) {
        float weightPitch = ego ? 0.74F : 0.82F;
        switch (move) {
            case SLASH -> level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, ego ? 1.38F : 1.08F, weightPitch);
            case STAB -> level.playSound(null, origin, SoundEvents.TRIDENT_THROW,
                    SoundSource.HOSTILE, ego ? 1.26F : 0.96F, weightPitch + 0.04F);
            case VERTICAL_SLASH -> {
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.HOSTILE, ego ? 1.55F : 1.22F, weightPitch - 0.12F);
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.HOSTILE, ego ? 1.16F : 0.92F, weightPitch - 0.20F);
            }
        }
    }

    public static void playAttackImpact(Level level, BlockPos origin,
                                        GeburahMove move, boolean ego) {
        switch (move) {
            case SLASH -> {
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.HOSTILE, ego ? 1.52F : 1.18F, ego ? 0.64F : 0.74F);
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.HOSTILE, ego ? 0.96F : 0.76F, ego ? 0.72F : 0.84F);
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.HOSTILE, ego ? 0.90F : 0.68F, ego ? 0.62F : 0.72F);
            }
            case STAB -> {
                level.playSound(null, origin, SoundEvents.TRIDENT_HIT,
                        SoundSource.HOSTILE, ego ? 1.58F : 1.22F, ego ? 0.62F : 0.72F);
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.HOSTILE, ego ? 1.16F : 0.88F, ego ? 0.66F : 0.78F);
                level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.HOSTILE, ego ? 1.02F : 0.74F, ego ? 0.60F : 0.70F);
            }
            case VERTICAL_SLASH -> {
                level.playSound(null, origin, SoundEvents.ANVIL_LAND,
                        SoundSource.HOSTILE, ego ? 2.25F : 1.72F, ego ? 0.50F : 0.58F);
                level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE,
                        SoundSource.HOSTILE, ego ? 1.88F : 1.42F, ego ? 0.56F : 0.66F);
                level.playSound(null, origin, SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.HOSTILE, ego ? 1.18F : 0.82F, ego ? 0.58F : 0.68F);
            }
        }
    }

    public static void playMajorImpact(Level level, BlockPos origin,
                                       GeburahMajorMove move, boolean finisher) {
        if (!finisher) {
            level.playSound(null, origin, SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.HOSTILE, 0.62F, 0.82F);
            return;
        }
        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, move == GeburahMajorMove.BODIES_EVERYWHERE
                        ? 1.55F : 1.12F, move == GeburahMajorMove.BODIES_EVERYWHERE
                        ? 0.62F : 0.74F);
        if (move == GeburahMajorMove.BODIES_EVERYWHERE) {
            level.playSound(null, origin, SoundEvents.ANVIL_LAND,
                    SoundSource.HOSTILE, 1.35F, 0.56F);
            level.playSound(null, origin, SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 1.12F, 0.58F);
        }
    }
}
