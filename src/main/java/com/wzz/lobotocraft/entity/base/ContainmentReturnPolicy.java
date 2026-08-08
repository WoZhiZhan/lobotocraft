package com.wzz.lobotocraft.entity.base;

import net.minecraft.resources.ResourceLocation;

/** Pure state rules for recoverable containment-return transactions. */
final class ContainmentReturnPolicy {
    private ContainmentReturnPolicy() {
    }

    static boolean shouldRetry(boolean pending, int tickCount, int interval) {
        return pending && interval > 0 && Math.floorMod(tickCount, interval) == 0;
    }

    static float healthAfterDeferredSuppression(float maxHealth) {
        return validMaxHealth(maxHealth);
    }

    static ResourceLocation selectTargetDimension(ResourceLocation home, ResourceLocation escape,
                                                    ResourceLocation current) {
        if (home != null) return home;
        if (escape != null) return escape;
        return current;
    }

    static LoadedState normalizeLoadedState(boolean escaped, boolean pending, boolean suppression,
                                            float health, float maxHealth) {
        if (!pending) {
            return new LoadedState(escaped, false, false, false, health);
        }

        float safeMax = validMaxHealth(maxHealth);
        float normalizedHealth = !Float.isFinite(health) || health <= 1.0F
                ? safeMax : Math.min(health, safeMax);
        // A legacy or manually edited save may contain Pending=true with HasEscaped=false.
        // Treat Pending as authoritative so the entity remains damageable while return is retried.
        return new LoadedState(true, true, true, suppression, normalizedHealth);
    }

    private static float validMaxHealth(float maxHealth) {
        return Float.isFinite(maxHealth) && maxHealth > 1.0F ? maxHealth : 1.0F;
    }

    record LoadedState(boolean escaped, boolean pending, boolean stopEventPending,
                       boolean suppression, float health) {
    }
}
