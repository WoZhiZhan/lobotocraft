package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.item.ego.children_galaxy.ChildrenGalaxyCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.BuffUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 友谊之证(原"鹅卵石"物品)。
 * 每 100 tick(5秒)根据来源的共享计数回血并恢复精神值。
 * 强度显示共享计数器的投影；恢复实时读取绑定的银河之子，而非首次领取的快照。
 * 保留现有 Minecraft 回复数值 counter * 2，本次只修计数来源与同步。
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
            int count = BuffUtil.getFriendshipProofCounter(player);
            if (count <= 0) return;
            float amount = count * 2.0f;
            if (EgoArmorHelper.isWearingFullSet(player, "children_galaxy")) {
                amount *= 2.0f;
            }
            if (ChildrenGalaxyCurio.hasFullSet(player)) {
                amount *= 1.5f;
            }
            player.heal(amount);
            MentalValueUtil.addMentalValue(player, amount);
        }
    }
}
