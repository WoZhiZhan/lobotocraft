package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.nothing_there.NothingThereCurio;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拟态"溢出加成"里的伤害部分：
 * 满套锁定 + 手持拟态 + 血量>100 时, 造成的伤害按 overhealBonus(0..50%) 提高。
 * (移速/攻速在 NothingThereCurio.curioTick 里以属性修饰符实现)
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class NothingThereHealthBuffEvent {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player player && !player.level().isClientSide) {
            float bonus = NothingThereCurio.overhealBonus(player);
            if (bonus > 0F) {
                event.setAmount(event.getAmount() * (1F + bonus));
            }
        }
    }
}