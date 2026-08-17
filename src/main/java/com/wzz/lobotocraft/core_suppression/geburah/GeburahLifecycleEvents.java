package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class GeburahLifecycleEvents {
    private GeburahLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onGeburahLoaded(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()
                || !(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof EntityGeburah geburah)) return;
        if (GeburahEncounterService.INSTANCE.reconcileLoadedEntity(geburah)) {
            event.setCanceled(true);
        }
    }
}
