package com.lobotocraft.facility.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DamageHistoryEvents {
    private DamageHistoryEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureRaw(LivingHurtEvent event) {
        if (!event.isCanceled()) DamageHistory.captureRaw(event);
    }

    /** Called in DamageSettlement's finally block so mental-only/canceled entries close last. */
    public static void finishHurt(LivingHurtEvent event) {
        DamageHistory.finishHurt(event);
    }

    /** Called after the final HP adapters by DamageSettlement. */
    public static void finishDamage(LivingDamageEvent event) {
        DamageHistory.finishDamage(event);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DamageHistory.clear(player.getUUID());
    }

    /**
     * 重生后清空历史：悬浮框语义是「本条命的死亡前受伤记录」，
     * 不清则上一条命的记录会与新一条命的混在一起。
     * 重生换了 ServerPlayer 实例但 UUID 不变，按 UUID 清即可；
     * 死亡消息在 die() 内构建，早于本事件，故不会影响本次死亡的悬浮内容。
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DamageHistory.clear(player.getUUID());
    }
}
