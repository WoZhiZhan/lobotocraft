package com.lobotocraft.facility.data;

/** Percentage rewards granted after an entire ordeal has been suppressed. */
public final class OrdealRewardPolicy {
    private OrdealRewardPolicy() {
    }

    public static int rewardPercent(OrdealTier tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case DAWN -> 10;
            case NOON -> 15;
            case DUSK -> 20;
            case MIDNIGHT -> 25;
            case NONE -> 0;
        };
    }

    /** Converts the percentage to an integer reward using nearest-integer rounding. */
    public static int rewardForMaximum(int maximum, OrdealTier tier) {
        int percent = rewardPercent(tier);
        if (maximum <= 0 || percent <= 0) return 0;
        long scaled = (long) maximum * percent;
        return (int) Math.min(Integer.MAX_VALUE, (scaled + 50L) / 100L);
    }
}
