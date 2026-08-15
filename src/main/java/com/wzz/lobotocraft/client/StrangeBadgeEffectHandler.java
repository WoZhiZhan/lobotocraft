package com.wzz.lobotocraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Compatibility tombstone for saves and integrations that still reference the retired effect. */
@OnlyIn(Dist.CLIENT)
public final class StrangeBadgeEffectHandler {
    private StrangeBadgeEffectHandler() {
    }

    public static void triggerEffect() {
        // Intentionally inert. Client file mutation and forced process exit are not valid gameplay effects.
    }
}
