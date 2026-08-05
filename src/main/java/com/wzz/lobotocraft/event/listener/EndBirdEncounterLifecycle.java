package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBird;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggEye;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggHigh;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggSmall;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replays persistent Apocalypse Bird cleanup when an affected entity is loaded again. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class EndBirdEncounterLifecycle {
    private EndBirdEncounterLifecycle() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof EntityEndBird endBird) {
            level.getServer().execute(() -> {
                if (!endBird.isRemoved() && !endBird.replayPendingAbort((ServerLevel) endBird.level())) {
                    endBird.replayPendingCompletion((ServerLevel) endBird.level());
                }
            });
            return;
        }
        if (entity instanceof ServerPlayer player) {
            level.getServer().execute(() -> cleanPlayer(player));
            return;
        }
        if (!isEndBirdEgg(entity)) return;
        level.getServer().execute(() -> cleanEgg(entity));
    }

    private static void cleanEgg(Entity egg) {
        if (egg.isRemoved() || !(egg.level() instanceof ServerLevel level)) return;
        EndBirdEggTracker tracker = EndBirdEggTracker.get(level);
        if (!tracker.needsEggCleanup(egg.getUUID())) return;
        egg.discard();
        if (egg.isRemoved()) tracker.acknowledgeEggCleanup(egg.getUUID());
    }

    private static void cleanPlayer(ServerPlayer player) {
        if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) return;
        EndBirdEggTracker tracker = EndBirdEggTracker.get(level);
        if (!tracker.needsCharmCleanup(player.getUUID())) return;
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        tracker.acknowledgeCharmCleanup(player.getUUID());
    }

    private static boolean isEndBirdEgg(Entity entity) {
        return entity instanceof EntityEndBirdEggSmall
                || entity instanceof EntityEndBirdEggHigh
                || entity instanceof EntityEndBirdEggEye;
    }
}
