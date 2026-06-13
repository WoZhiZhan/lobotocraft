package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * 蝴蝶缠身(亡蝶葬仪的处决 buff)。
 * 持续6秒:固定玩家为游泳姿势、最高等级缓慢与失明、身上持续绽放蝴蝶特效;
 * buff 结束时玩家死亡(由 ButterflyFuneralEvent 监听效果到期处理,死亡提示为"被亡蝶葬仪救赎")。
 */
public class ButterflyShroudEffect extends MobEffect {

    public ButterflyShroudEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8E8FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // 每tick生效
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (!(living instanceof Player player)) return;
        // 固定为游泳姿势
        player.setPose(Pose.SWIMMING);
        player.setSwimming(true);
        // 最高等级缓慢与失明(短时叠加,持续刷新)
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 255, false, false, false));
    }
}
