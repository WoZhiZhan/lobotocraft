package com.lobotocraft.facility.data;

import net.minecraft.util.RandomSource;

/** Original-game ordeal families available at each late tier. */
public final class OrdealColorPool {
    public static final int CRIMSON = 1;
    public static final int GREEN = 2;
    public static final int VIOLET = 3;
    public static final int AMBER = 4;

    private static final int[] DUSK = {CRIMSON, GREEN, AMBER};
    private static final int[] MIDNIGHT = {GREEN, VIOLET, AMBER};

    private OrdealColorPool() {
    }

    public static int randomDawn(RandomSource random) {
        return CRIMSON + random.nextInt(4);
    }

    public static int randomDifferentDawn(RandomSource random, int excludedColor) {
        if (excludedColor < CRIMSON || excludedColor > AMBER) return randomDawn(random);
        int color = CRIMSON + random.nextInt(3);
        return color >= excludedColor ? color + 1 : color;
    }

    /** Noon keeps the legacy event IDs: 1 indigo, 2 violet, 3 green, 4 crimson. */
    public static int randomNoon(int day, RandomSource random) {
        int[] pool = day >= 27
                ? new int[]{1, 2, 3, 4}
                : new int[]{2, 3, 4};
        return pool[random.nextInt(pool.length)];
    }

    public static int randomDusk(RandomSource random) {
        return DUSK[random.nextInt(DUSK.length)];
    }

    public static int randomMidnight(RandomSource random) {
        return MIDNIGHT[random.nextInt(MIDNIGHT.length)];
    }

    public static boolean supports(OrdealTier tier, int color) {
        return switch (tier) {
            case DAWN, NOON -> color >= 1 && color <= 4;
            case DUSK -> contains(DUSK, color);
            case MIDNIGHT -> contains(MIDNIGHT, color);
            default -> false;
        };
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) return true;
        }
        return false;
    }
}
