package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class LonelinessEffect extends MobEffect {
    private static final String SPEED_UUID = "dafec058-670a-41f5-a57b-d076ede10d3b";
    private static final double SPEED_REDUCTION_PER_LEVEL = 0.05;
    private static final int MAX_LEVEL = 9;

    public LonelinessEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A1A1A);
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                SPEED_UUID,
                -SPEED_REDUCTION_PER_LEVEL,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        int level = Math.min(amplifier, MAX_LEVEL); // 想让 I 级就减速的话改成 amplifier + 1
        return Math.max(modifier.getAmount() * level, -0.9); // 最低保留 10% 速度
    }
}