package com.lobotocraft.facility.data;

/** Availability gate for ordeal tiers that do not yet have production content. */
public final class OrdealContentPolicy {
    private OrdealContentPolicy() {
    }

    public static boolean isUnfinished(OrdealTier tier) {
        return tier == OrdealTier.DUSK || tier == OrdealTier.MIDNIGHT;
    }
}
