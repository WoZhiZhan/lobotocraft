package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TrackingNameSync {

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof AbstractAbnormality ent)) return;

        boolean unlocked = sp.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .map(data -> data.getObservationLevel(ent.getAbnormalityCode()) >= 1)
                .orElse(false);

        Component show = unlocked
                ? Component.literal(ent.getAbnormalityName())
                : Component.literal(ent.getAbnormalityCode());

        ent.sendPerPlayerName(sp, show);
    }
}
