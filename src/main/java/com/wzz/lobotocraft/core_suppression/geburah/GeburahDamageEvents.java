package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class GeburahDamageEvents {
    private GeburahDamageEvents() {
    }

    /** Custom posture/health application, last in DamageSettlement's final HP phase. */
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityGeburah geburah) || event.getAmount() <= 0.0F) return;
        LivingEntity responsible = GeburahDamageResponsibility.resolve(event.getSource());
        geburah.applyFinalDamage(event.getAmount(), GeburahPosturePolicy.takesPostureDamageFrom(
                responsible instanceof ServerPlayer));
        event.setAmount(0.0F);
    }
}
