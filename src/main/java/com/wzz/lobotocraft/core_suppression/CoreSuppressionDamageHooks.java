package com.wzz.lobotocraft.core_suppression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class CoreSuppressionDamageHooks {
    private CoreSuppressionDamageHooks() {
    }

    public static float incomingDamageMultiplier(ServerPlayer target, DamageSource source) {
        CoreSuppressionData suppression = CoreSuppressionData.get(target.serverLevel());
        CoreSuppressionType type = suppression.getActiveType();
        if (!suppression.isActive() || type == null) return 1.0F;
        float multiplier = CoreSuppressionEncounterRegistry.incomingDamageMultiplier(
                type, target, source);
        return Float.isFinite(multiplier) && multiplier > 0.0F ? multiplier : 1.0F;
    }
}
