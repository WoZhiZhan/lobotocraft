package com.wzz.lobotocraft.core_suppression.chesed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public final class ChesedDamagePolicy {
    public static final float AMPLIFIED_DAMAGE_MULTIPLIER = 5.0F;
    public static final int ALL_DAMAGE_MASK = (1 << ChesedDamageKind.values().length) - 1;

    private ChesedDamagePolicy() {
    }

    public static int selectionCount(int overloadLevel) {
        if (overloadLevel >= 5) return 3;
        if (overloadLevel >= 2) return 2;
        return 1;
    }

    public static int visualStage(int selectedMask) {
        return Math.max(0, Math.min(2, Integer.bitCount(selectedMask & ALL_DAMAGE_MASK) - 1));
    }

    public static int reroll(int overloadLevel, int previousMask, IntUnaryOperator nextInt) {
        int count = selectionCount(overloadLevel);
        List<ChesedDamageKind> candidates = new ArrayList<>(List.of(ChesedDamageKind.values()));
        if (count == 1) {
            candidates.remove(ChesedDamageKind.PALE);
            if (Integer.bitCount(previousMask & ALL_DAMAGE_MASK) == 1) {
                candidates.removeIf(kind -> kind.isSelected(previousMask));
            }
        }

        int selectedMask = 0;
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(nextInt.applyAsInt(candidates.size()), candidates.size());
            selectedMask |= candidates.remove(index).bit();
        }
        return selectedMask;
    }

    public static float multiplier(int selectedMask, ChesedDamageKind damageKind) {
        return damageKind != null && damageKind.isSelected(selectedMask)
                ? AMPLIFIED_DAMAGE_MULTIPLIER : 1.0F;
    }
}
