package com.wzz.lobotocraft.core_suppression;

import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalkuthWorkPolicyTest {
    @Test
    void generatedMappingsArePermutationsAndNeverCompletelyUnchanged() {
        Random random = new Random(9137L);
        for (int index = 0; index < 1_000; index++) {
            assertTrue(MalkuthWorkPolicy.isValidMapping(
                    MalkuthWorkPolicy.generateMapping(random::nextInt)));
        }
    }

    @Test
    void selectedSlotsMapToTheSharedActualWorkTypes() {
        int[] mapping = {1, 3, 0, 2};

        assertEquals(WorkType.INSIGHT,
                MalkuthWorkPolicy.map(WorkType.INSTINCT, mapping));
        assertEquals(WorkType.REPRESSION,
                MalkuthWorkPolicy.map(WorkType.INSIGHT, mapping));
        assertEquals(WorkType.INSTINCT,
                MalkuthWorkPolicy.map(WorkType.ATTACHMENT, mapping));
        assertEquals(WorkType.ATTACHMENT,
                MalkuthWorkPolicy.map(WorkType.REPRESSION, mapping));
    }

    @Test
    void invalidLegacyMappingsFallBackWithoutChangingTheSelection() {
        assertFalse(MalkuthWorkPolicy.isValidMapping(new int[]{0, 1, 2, 3}));
        assertFalse(MalkuthWorkPolicy.isValidMapping(new int[]{1, 1, 2, 3}));
        assertEquals(WorkType.ATTACHMENT,
                MalkuthWorkPolicy.map(WorkType.ATTACHMENT, new int[]{0, 1, 2, 3}));
    }

    @Test
    void activeMappingAndObservedLevelSurviveSaveReload() {
        CoreSuppressionData data = new CoreSuppressionData();
        data.start(CoreSuppressionType.MALKUTH, UUID.randomUUID(), "manager", 26,
                0, 0, Map.of());
        data.setMalkuthWorkState(new int[]{2, 0, 3, 1}, 4);

        CoreSuppressionData loaded = CoreSuppressionData.load(data.save(new CompoundTag()));

        assertTrue(loaded.hasMalkuthWorkMapping());
        assertArrayEquals(new int[]{2, 0, 3, 1}, loaded.getMalkuthWorkMapping());
        assertEquals(4, loaded.getMalkuthObservedMeltdownLevel());
    }

    @Test
    void oldSavesStartWithoutAStaleMappingAndCompletionClearsIt() {
        CoreSuppressionData legacy = CoreSuppressionData.load(new CompoundTag());
        assertFalse(legacy.hasMalkuthWorkMapping());
        assertEquals(-1, legacy.getMalkuthObservedMeltdownLevel());

        legacy.setMalkuthWorkState(new int[]{1, 0, 3, 2}, 2);
        legacy.finish(false);
        assertFalse(legacy.hasMalkuthWorkMapping());
        assertEquals(-1, legacy.getMalkuthObservedMeltdownLevel());
    }

    @Test
    void cancellationLocksAfterOriginalLevelThreeAndRewardRemainsTenPercent() {
        assertFalse(MalkuthWorkPolicy.isCancellationLockedForStoredLevel(3));
        assertTrue(MalkuthWorkPolicy.isCancellationLockedForStoredLevel(4));
        assertEquals(0.10F, CoreSuppressionManager.MALKUTH_COMPLETION_SUCCESS_BONUS);
    }
}
