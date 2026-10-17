package com.wzz.lobotocraft.combat;

import com.wzz.lobotocraft.event.ForgeModEvent;
import com.wzz.lobotocraft.event.listener.*;
import com.wzz.lobotocraft.util.DotHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageSettlementOwnershipTest {
    @Test void migratedModifiersCannotAlsoSubscribeIndependently() throws Exception {
        for (Class<?> hook : new Class<?>[]{EgoAttackChargeHandler.class, NothingThereHealthBuffEvent.class,
                SheepskinSetHandler.class, QueenBeeSetHandler.class, ButterflyFuneralEvents.class,
                BlueStarSetEvent.class, DotHelper.class}) {
            assertNull(hook.getMethod("onLivingHurt", LivingHurtEvent.class).getAnnotation(SubscribeEvent.class),
                    hook.getSimpleName() + " would apply twice");
        }
        assertNull(ForgeModEvent.class.getMethod("applyOutgoingModifiers", LivingHurtEvent.class)
                .getAnnotation(SubscribeEvent.class));
        assertNull(ForgeModEvent.class.getMethod("applyIncomingModifiers", LivingHurtEvent.class)
                .getAnnotation(SubscribeEvent.class));
        assertNull(SkadiEvent.class.getMethod("onDamage", LivingDamageEvent.class).getAnnotation(SubscribeEvent.class));
        assertNull(DotHelper.class.getMethod("onLivingDamage", LivingDamageEvent.class).getAnnotation(SubscribeEvent.class));
        assertNotNull(DamageSettlement.class.getMethod("onHurt", LivingHurtEvent.class).getAnnotation(SubscribeEvent.class));
        assertNotNull(DamageSettlement.class.getMethod("onDamage", LivingDamageEvent.class).getAnnotation(SubscribeEvent.class));
    }
}
