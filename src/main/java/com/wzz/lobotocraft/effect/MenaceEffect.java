package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class MenaceEffect extends MobEffect {
    public MenaceEffect() {
        super(MobEffectCategory.HARMFUL, 0xAEE6FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        Level level = living.level();
        if (!level.isClientSide() && living.tickCount % 40 == 0) {
            ParticleUtil.spawnParticlesAroundEntity(living, ModParticleTypes.GOLD_LIGHT.get(), 20, 0.1d);
        }
    }
}