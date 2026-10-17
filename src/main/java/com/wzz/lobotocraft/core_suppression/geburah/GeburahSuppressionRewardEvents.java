package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class GeburahSuppressionRewardEvents {
    private GeburahSuppressionRewardEvents() {
    }

    /** Invoked in the explicit DamageSettlement order; not an independent listener. */
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) return;

        LivingEntity responsible = GeburahDamageResponsibility.resolve(event.getSource());
        if (!(responsible instanceof ServerPlayer attacker)) return;

        boolean eligibleTarget = isRewardTarget(event.getEntity());
        boolean rewardUnlocked = attacker instanceof EmployeeServerPlayer
                ? CoreSuppressionData.get(attacker.serverLevel())
                        .hasCompleted(CoreSuppressionType.GEBURAH)
                : CoreSuppressionManager.hasReward(attacker, CoreSuppressionType.GEBURAH);
        event.setAmount(GeburahSuppressionRewardPolicy.combatDamage(
                event.getAmount(), rewardUnlocked, eligibleTarget));
    }

    static boolean isRewardTarget(LivingEntity target) {
        return target instanceof AbstractAbnormality
                || target instanceof OrdealEntity
                || (target instanceof EntityBasinSeaborn seaborn && seaborn.isBlueMiddaySpawn());
    }
}
