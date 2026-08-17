package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.GeburahStatusSyncPacket;
import com.wzz.lobotocraft.util.DotHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class GeburahBleedSync {
    public static final int DURATION_TICKS = 140;

    private GeburahBleedSync() {
    }

    public static void apply(LivingEntity attacker, LivingEntity target, int stacks) {
        int safeStacks = Math.max(0, Math.min(5, stacks));
        for (int i = 0; i < safeStacks; i++) {
            DotHelper.applyDot(DotHelper.GEBURAH_BLEED, attacker, target,
                    1.0F, "red", 40, DURATION_TICKS, 5);
        }
        sync(target, DURATION_TICKS);
    }

    public static void clear(LivingEntity target) {
        DotHelper.clearDot(target, DotHelper.GEBURAH_BLEED);
        sync(target, 0);
    }

    public static void sync(LivingEntity target, int remainingTicks) {
        if (!(target instanceof ServerPlayer player)) return;
        int stacks = DotHelper.getStacks(target, DotHelper.GEBURAH_BLEED);
        MessageLoader.getLoader().sendToPlayer(player,
                new GeburahStatusSyncPacket(stacks, stacks == 0 ? 0 : remainingTicks));
    }

    public static void sync(LivingEntity target) {
        sync(target, DotHelper.getRemainingTicks(target, DotHelper.GEBURAH_BLEED));
    }
}
