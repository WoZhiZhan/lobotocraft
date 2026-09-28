package com.wzz.lobotocraft.client.fear;

import com.wzz.lobotocraft.ModMain;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps level-five fear audio exclusive to the tinnitus layer. */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class FearSoundBlocker {
    private FearSoundBlocker() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (FearClientState.blocksSound(event.getSound())) event.setSound(null);
    }
}
