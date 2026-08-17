package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class SuppressionRewardPolicy {
    private SuppressionRewardPolicy() {
    }

    public static boolean shouldGrant(@Nullable Entity directKiller,
                                      @Nullable LivingEntity killCredit) {
        return shouldGrantForTypes(
                directKiller == null ? null : directKiller.getClass(),
                killCredit == null ? null : killCredit.getClass());
    }

    static boolean shouldGrantForTypes(@Nullable Class<? extends Entity> directType,
                                       @Nullable Class<? extends Entity> creditType) {
        return !isGeburah(directType) && !isGeburah(creditType);
    }

    private static boolean isGeburah(@Nullable Class<? extends Entity> type) {
        return type != null && EntityGeburah.class.isAssignableFrom(type);
    }
}
