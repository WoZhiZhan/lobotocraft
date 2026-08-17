package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class GeburahLifecycleEvents {
    private GeburahLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CoreSuppressionData suppression = CoreSuppressionData.get(event.getServer().overworld());
        if (suppression.getActiveType() == CoreSuppressionType.GEBURAH) {
            CoreSuppressionManager.failChallenge(event.getServer(), "服务器重启导致战斗中断");
        }
        GeburahEncounterService.INSTANCE.reconcileServerState(event.getServer());
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
