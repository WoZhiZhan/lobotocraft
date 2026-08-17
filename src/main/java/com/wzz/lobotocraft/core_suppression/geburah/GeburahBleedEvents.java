package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.util.DotHelper;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class GeburahBleedEvents {
    private GeburahBleedEvents() {
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (DotHelper.getStacks(event.getEntity(), DotHelper.GEBURAH_BLEED) > 0) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }
}
