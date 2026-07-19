package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityLeticia;
import com.wzz.lobotocraft.event.definition.work.WorkStartEvent;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class LeticiaGiftEvent {
    private static final int GIFT_DURATION = 20 * 60 * 20;
    private static final int SHAKE_INTERVAL_TICKS = 40;
    private static final int SHAKE_TICKS = 10;
    private static final float SHAKE_INTENSITY = 3.0F;

    @SubscribeEvent
    public static void onWorkStart(WorkStartEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getAbnormality() instanceof EntityLeticia) {
            return;
        }

        if (player.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get())) {
            player.level().playSound(null, player.blockPosition(), ModSounds.WORKER_BEE_SPAWN.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            player.removeEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get());
            EntityLeticia.spawnFriendFor(player);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            event.setCancelReason("");
            event.setCanceled(true);
            return;
        }

        if (player.hasEffect(ModMobEffects.LETICIA_GIFT.get())) {
            player.removeEffect(ModMobEffects.LETICIA_GIFT.get());
            player.addEffect(new MobEffectInstance(ModMobEffects.LETICIA_BROKEN_GIFT.get(),
                    GIFT_DURATION, 0, false, true, true));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.removeEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)
                || !player.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get())) {
            return;
        }
        if (player.tickCount % SHAKE_INTERVAL_TICKS == 0) {
            MessageLoader.getLoader().sendToPlayer(player,
                    new TriggerShakePacket(SHAKE_TICKS, SHAKE_INTENSITY));
        }
    }
}
