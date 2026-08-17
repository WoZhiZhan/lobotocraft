package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class GeburahParticipantEvents {
    private GeburahParticipantEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onParticipantDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().dimension() != ModDimensions.LOBOTO_KEY
                || CoreSuppressionData.get(player.serverLevel()).getActiveType()
                != CoreSuppressionType.GEBURAH) return;

        GeburahEncounterData.DeathOutcome outcome =
                GeburahEncounterService.INSTANCE.consumeParticipantDeath(player);
        if (outcome == GeburahEncounterData.DeathOutcome.IGNORE) return;

        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.invulnerableTime = 20;
        if (outcome == GeburahEncounterData.DeathOutcome.REVIVE) {
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.core_suppression.geburah.revived"));
        } else {
            player.setGameMode(GameType.SPECTATOR);
            player.getPersistentData().putBoolean(
                    GeburahEncounterService.FORCED_SPECTATOR_TAG, true);
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.core_suppression.geburah.eliminated"));
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GeburahEncounterService.INSTANCE.enforceDimensionLock(player, event.getFrom());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GeburahEncounterService.INSTANCE.cleanupLoginResidue(player);
        }
    }
}
