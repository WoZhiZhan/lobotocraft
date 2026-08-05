package com.wzz.lobotocraft.entity.ordeal;

import com.lobotocraft.facility.data.OrdealColorPool;
import com.lobotocraft.facility.data.OrdealTier;

/** Variants retained for save compatibility with placeholder late-ordeal entity IDs. */
public enum LateOrdealVariant {
    CRIMSON_DUSK(OrdealTier.DUSK, OrdealColorPool.CRIMSON, "crimson_noon", "red", false),
    GREEN_DUSK(OrdealTier.DUSK, OrdealColorPool.GREEN, "green_noon", "black", true),
    AMBER_DUSK(OrdealTier.DUSK, OrdealColorPool.AMBER, "amber_dawn", "red", false),
    GREEN_MIDNIGHT(OrdealTier.MIDNIGHT, OrdealColorPool.GREEN, "green_noon", "black", true),
    VIOLET_MIDNIGHT(OrdealTier.MIDNIGHT, OrdealColorPool.VIOLET, "violet_noon", "black", true),
    AMBER_MIDNIGHT(OrdealTier.MIDNIGHT, OrdealColorPool.AMBER, "amber_dawn", "red", false);

    private final OrdealTier tier;
    private final int color;
    private final String assetName;
    private final String damageType;
    private final boolean stationary;

    LateOrdealVariant(OrdealTier tier, int color, String assetName, String damageType, boolean stationary) {
        this.tier = tier;
        this.color = color;
        this.assetName = assetName;
        this.damageType = damageType;
        this.stationary = stationary;
    }

    public OrdealTier tier() { return tier; }
    public int color() { return color; }
    public String assetName() { return assetName; }
    public String damageType() { return damageType; }
    public boolean stationary() { return stationary; }

    public static LateOrdealVariant resolve(OrdealTier tier, int color) {
        for (LateOrdealVariant variant : values()) {
            if (variant.tier == tier && variant.color == color) return variant;
        }
        return null;
    }
}
