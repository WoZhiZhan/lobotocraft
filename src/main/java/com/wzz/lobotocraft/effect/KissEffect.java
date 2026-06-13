package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 亲吻(原"冰雪女皇的冰片"物品)。
 * 原物品仅作为冰雪女皇决斗流程的标记使用(无主动作用),
 * 故此效果同样仅作标记,不产生任何 tick 行为。
 */
public class KissEffect extends MobEffect {

    public KissEffect() {
        super(MobEffectCategory.NEUTRAL, 0xAEE6FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        // 纯标记,无行为
    }
}
