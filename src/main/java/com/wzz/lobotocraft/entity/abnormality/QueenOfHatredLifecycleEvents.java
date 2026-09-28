package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.event.definition.company.FacilityQliphothStageEvent;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class QueenOfHatredLifecycleEvents {
    private QueenOfHatredLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onWorkComplete(WorkCompleteEvent event) {
        if (event.getAbnormality() instanceof EntityQueenOfHatred queen) {
            queen.recordCompletedWork(event.getEntity(), event.getWorkResult(),
                    event.getPeOutput(), event.isForcedEnd());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStaffDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)
                || !QueenOfHatredStaff.isStaff(event.getEntity())) return;
        notifyQueens(level, queen -> queen.onStaffDeath(event.getEntity()));
    }

    @SubscribeEvent
    public static void onQliphothStage(FacilityQliphothStageEvent event) {
        notifyQueens(event.getLevel(), queen -> queen.onFacilityQliphothStage(event.getTriggeredLevel()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityQueenOfHatred queen && queen.isAlive() && !queen.isRemoved()) {
                    queen.syncVisualToPlayer(player);
                }
            }
        }
    }

    private static void notifyQueens(ServerLevel level,
                                     java.util.function.Consumer<EntityQueenOfHatred> action) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityQueenOfHatred queen && queen.isAlive() && !queen.isRemoved()) {
                action.accept(queen);
            }
        }
    }
}
