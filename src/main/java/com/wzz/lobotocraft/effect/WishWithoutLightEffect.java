package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 同葬无光之愿(浊心斯卡蒂的祝福)。
 * 与斯卡蒂工作后获得:
 *  - 受到四种颜色(WHITE/RED/BLACK/PALE)的伤害减少 40%(在 LivingHurtEvent 中处理)
 *  - 每 3 秒回复 10 点生命值与 10 点精神值
 *  - 死亡时原地满血免死复活(在死亡事件中处理),斯卡蒂计数器 -1
 * 该效果在浊心斯卡蒂计数器归零后失效(由斯卡蒂归零逻辑统一移除)。
 */
public class WishWithoutLightEffect extends MobEffect {

    public WishWithoutLightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1A2A4A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每 60 tick(3秒)触发一次
        return duration % 60 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (living instanceof ServerPlayer player) {
            if (player.isDeadOrDying()) return;
            player.heal(10f);
            MentalValueUtil.addMentalValue(player, 10f);
        }
    }
}
