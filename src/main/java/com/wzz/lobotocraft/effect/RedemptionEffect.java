package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 救赎 —— 挂在被圣宣持有者打中的生物身上。
 * 每层使其受到该持有者的伤害提高 1%，最多 50 层，持续 25 秒，每次攻击刷新。
 * 数值在 ButterflyFuneralEvents#onLivingHurt 里结算。
 */
public class RedemptionEffect extends MobEffect {

    public RedemptionEffect() {
        super(MobEffectCategory.HARMFUL, 0xC8A2C8);
    }
}