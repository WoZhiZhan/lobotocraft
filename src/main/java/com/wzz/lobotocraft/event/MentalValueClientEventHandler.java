package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class MentalValueClientEventHandler {

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            if (mental.isMentalValueEmpty()) {
                float maxHealth = player.getMaxHealth();
                float maxMental = mental.getEffectiveMaxMentalValue();
                if (maxHealth > maxMental) {
                    event.getInput().forwardImpulse = 0;
                    event.getInput().leftImpulse = 0;
                    event.getInput().jumping = false;
                    event.getInput().shiftKeyDown = false;
                }
            }
        });
    }
}