package com.wzz.lobotocraft.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class MustOpenPathBoundaryEffect extends MobEffect {
    public MustOpenPathBoundaryEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x36C9E8);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (!(living.level() instanceof ServerLevel level)) return;
        double angle = living.tickCount * 0.38D;
        for (int i = 0; i < 4; i++) {
            double current = angle + Math.PI * 0.5D * i;
            double radius = 0.7D + (i % 2) * 0.25D;
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                    living.getX() + Math.cos(current) * radius,
                    living.getY() + 0.2D + (living.tickCount + i * 7) % 25 / 25.0D * living.getBbHeight(),
                    living.getZ() + Math.sin(current) * radius,
                    1, 0.03D, 0.04D, 0.03D, 0.01D);
        }
        if (living.tickCount % 10 == 0) {
            level.playSound(null, living.blockPosition(), SoundEvents.PLAYER_SWIM,
                    SoundSource.PLAYERS, 0.45F, 0.9F + living.getRandom().nextFloat() * 0.2F);
        }
    }
}
