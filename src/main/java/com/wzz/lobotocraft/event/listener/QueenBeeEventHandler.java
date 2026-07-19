package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.effect.QueenBeeSporeEffect;
import com.wzz.lobotocraft.init.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class QueenBeeEventHandler {
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide && living.hasEffect(ModMobEffects.QUEEN_BEE_SPORE.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.level().isClientSide && dead.hasEffect(ModMobEffects.QUEEN_BEE_SPORE.get())) {
            QueenBeeSporeEffect.spawnWorkerFromCorpse(dead);
        }
    }
}
