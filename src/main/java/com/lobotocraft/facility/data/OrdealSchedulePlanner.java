package com.lobotocraft.facility.data;

import net.minecraft.util.RandomSource;

/** Pure deterministic planner for a single company's day. */
public final class OrdealSchedulePlanner {
    public static final int DAWN_START_DAY = 3;
    public static final int NOON_START_DAY = 11;
    public static final int DUSK_START_DAY = 21;
    public static final int MIDNIGHT_START_DAY = 26;
    private static final long DAY_SALT = 0x9E3779B97F4A7C15L;
    private static final long SCHEDULE_SALT = 0x4C4F424F544F4352L;

    private OrdealSchedulePlanner() {
    }

    public static boolean isUnlocked(int day, OrdealTier tier) {
        if (tier == null || tier == OrdealTier.NONE) return true;
        return switch (tier) {
            case DAWN -> day >= DAWN_START_DAY;
            case NOON -> day >= NOON_START_DAY;
            case DUSK -> day >= DUSK_START_DAY;
            case MIDNIGHT -> day >= MIDNIGHT_START_DAY;
            default -> true;
        };
    }

    public static Plan create(int day, long worldSeed) {
        OrdealTier[] tiers = new OrdealTier[10];
        int[] colors = new int[10];
        java.util.Arrays.fill(tiers, OrdealTier.NONE);
        RandomSource random = RandomSource.create(
            worldSeed ^ ((long) Math.max(1, day) * DAY_SALT) ^ SCHEDULE_SALT);

        if (day >= DAWN_START_DAY) {
            // This project schedules two Dawn ordeals at levels II and III.
            tiers[2] = OrdealTier.DAWN;
            colors[2] = OrdealColorPool.randomDawn(random);
            tiers[3] = OrdealTier.DAWN;
            colors[3] = OrdealColorPool.randomDifferentDawn(random, colors[2]);
        }

        if (day >= NOON_START_DAY) {
            // Level III belongs to the second Dawn, so Noon uses IV or V.
            int noonStage = 4 + random.nextInt(2);
            tiers[noonStage] = OrdealTier.NOON;
            colors[noonStage] = OrdealColorPool.randomNoon(day, random);
        }

        if (day >= DUSK_START_DAY) {
            // Dusk is previewed at VI with 50%, otherwise guaranteed at VII.
            int duskStage = random.nextBoolean() ? 6 : 7;
            tiers[duskStage] = OrdealTier.DUSK;
            colors[duskStage] = OrdealColorPool.randomDusk(random);
        }

        if (day >= MIDNIGHT_START_DAY) {
            tiers[8] = OrdealTier.MIDNIGHT;
            colors[8] = OrdealColorPool.randomMidnight(random);
        }
        return new Plan(tiers, colors);
    }

    public record Plan(OrdealTier[] tiers, int[] colors) {
        public Plan {
            tiers = tiers.clone();
            colors = colors.clone();
        }

        public OrdealTier tierAt(int stage) {
            return stage >= 0 && stage < tiers.length && tiers[stage] != null
                ? tiers[stage] : OrdealTier.NONE;
        }

        public int colorAt(int stage) {
            return stage >= 0 && stage < colors.length ? colors[stage] : 0;
        }
    }
}
