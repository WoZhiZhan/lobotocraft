package com.wzz.lobotocraft.core_suppression;

import com.wzz.lobotocraft.work.WorkType;

import java.util.Arrays;

/** Pure rules for Malkuth's scrambled work-command slots. */
public final class MalkuthWorkPolicy {
    public static final int WORK_TYPE_COUNT = 4;

    @FunctionalInterface
    public interface BoundedRandom {
        int nextInt(int bound);
    }

    private MalkuthWorkPolicy() {
    }

    /**
     * Generates one global slot-to-work mapping. The completely unchanged
     * order is excluded, matching the original suppression.
     */
    public static int[] generateMapping(BoundedRandom random) {
        int[] mapping = identityMapping();
        int attempts = 0;
        do {
            for (int index = mapping.length - 1; index > 0; index--) {
                int swapIndex = random.nextInt(index + 1);
                int value = mapping[index];
                mapping[index] = mapping[swapIndex];
                mapping[swapIndex] = value;
            }
        } while (isIdentity(mapping) && ++attempts < 32);
        if (isIdentity(mapping)) {
            mapping[0] = 1;
            mapping[1] = 0;
        }
        return mapping;
    }

    public static WorkType map(WorkType selectedSlot, int[] mapping) {
        if (selectedSlot == null || !isValidMapping(mapping)) return selectedSlot;
        return WorkType.values()[mapping[selectedSlot.ordinal()]];
    }

    public static boolean isValidMapping(int[] mapping) {
        if (mapping == null || mapping.length != WORK_TYPE_COUNT || isIdentity(mapping)) return false;
        boolean[] seen = new boolean[WORK_TYPE_COUNT];
        for (int value : mapping) {
            if (value < 0 || value >= WORK_TYPE_COUNT || seen[value]) return false;
            seen[value] = true;
        }
        return true;
    }

    public static int[] normalizedCopy(int[] mapping) {
        return isValidMapping(mapping) ? Arrays.copyOf(mapping, mapping.length) : new int[0];
    }

    /** FacilityDayData stores the next overload level, so completed III is stored as IV. */
    public static boolean isCancellationLockedForStoredLevel(int storedMeltdownLevel) {
        return storedMeltdownLevel >= 4;
    }

    private static int[] identityMapping() {
        return new int[]{0, 1, 2, 3};
    }

    private static boolean isIdentity(int[] mapping) {
        if (mapping == null || mapping.length != WORK_TYPE_COUNT) return false;
        for (int index = 0; index < mapping.length; index++) {
            if (mapping[index] != index) return false;
        }
        return true;
    }
}
