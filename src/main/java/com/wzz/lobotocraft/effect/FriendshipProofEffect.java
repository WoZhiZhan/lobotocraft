package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 友谊之证(原"鹅卵石"物品)。
 * 作用与原物品完全一致:每 100 tick(5秒)根据强度回血并恢复精神值。
 * 强度(amplifier)承载原 QliphothCounter:counter = amplifier + 1。
 * 即 amplifier=0 对应 counter=1,回血/回精神值 = counter * 2。
 */
public class FriendshipProofEffect extends MobEffect {

    public FriendshipProofEffect() {
        // 有益效果,颜色取银河紫色调
        super(MobEffectCategory.BENEFICIAL, 0x9B59B6);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每 100 tick 触发一次,与原 inventoryTick (tickCount % 100 == 0) 一致
        return duration % 100 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (living instanceof ServerPlayer player) {
            if (player.isDeadOrDying()) {
                return;
            }
            int count = amplifier + 1; // 还原 QliphothCounter
            player.heal(count * 2);
            MentalValueUtil.addMentalValue(player, count * 2);
        }
    }
}
