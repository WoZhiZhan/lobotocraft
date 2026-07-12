package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class LonelinessEffect extends MobEffect {
    private static final double SPEED_REDUCTION_PER_LEVEL = 0.05;
    private static final int MAX_AMPLIFIER = 9;
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("dafec058-670a-41f5-a57b-d076ede10d3b");

    public LonelinessEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A1A1A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        int effectiveAmplifier = Math.min(amplifier, MAX_AMPLIFIER);
        double speedReduction = effectiveAmplifier * SPEED_REDUCTION_PER_LEVEL;
        double speedMultiplier = 1.0 - speedReduction;
        speedMultiplier = Math.max(speedMultiplier, 0.1);
        if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            living.getAttribute(Attributes.MOVEMENT_SPEED)
                    .addTransientModifier(new AttributeModifier(
                            SPEED_MODIFIER_UUID,
                            "loneliness_speed_reduction",
                            speedMultiplier - 1.0,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    ));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity living, AttributeMap modifier, int amplifier) {
        living.getAttribute(Attributes.MOVEMENT_SPEED)
                .removeModifier(SPEED_MODIFIER_UUID);
        super.removeAttributeModifiers(living, modifier, amplifier);
    }
}