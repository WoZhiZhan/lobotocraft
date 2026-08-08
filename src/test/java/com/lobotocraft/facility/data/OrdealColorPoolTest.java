package com.lobotocraft.facility.data;

import com.wzz.lobotocraft.entity.ordeal.LateOrdealVariant;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrdealColorPoolTest {
    @Test
    void secondDawnColorAlwaysDiffersFromFirst() {
        RandomSource random = RandomSource.create(17L);
        for (int first = OrdealColorPool.CRIMSON; first <= OrdealColorPool.AMBER; first++) {
            for (int i = 0; i < 200; i++) {
                assertNotEquals(first, OrdealColorPool.randomDifferentDawn(random, first));
            }
        }
    }

    @Test
    void indigoNoonUnlocksOnlyAfterDisplayedDayTwentySix() {
        Set<Integer> early = new HashSet<>();
        Set<Integer> late = new HashSet<>();
        RandomSource earlyRandom = RandomSource.create(31L);
        RandomSource lateRandom = RandomSource.create(31L);
        for (int i = 0; i < 500; i++) {
            early.add(OrdealColorPool.randomNoon(26, earlyRandom));
            late.add(OrdealColorPool.randomNoon(27, lateRandom));
        }

        assertEquals(Set.of(2, 3, 4), early);
        assertEquals(Set.of(1, 2, 3, 4), late);
    }

    @Test
    void duskUsesOnlyOriginalFamilies() {
        Set<Integer> seen = new HashSet<>();
        RandomSource random = RandomSource.create(99L);
        for (int i = 0; i < 500; i++) seen.add(OrdealColorPool.randomDusk(random));

        assertEquals(Set.of(OrdealColorPool.CRIMSON, OrdealColorPool.GREEN, OrdealColorPool.AMBER), seen);
        assertFalse(OrdealColorPool.supports(OrdealTier.DUSK, OrdealColorPool.VIOLET));
    }

    @Test
    void midnightUsesOnlyOriginalFamilies() {
        Set<Integer> seen = new HashSet<>();
        RandomSource random = RandomSource.create(101L);
        for (int i = 0; i < 500; i++) seen.add(OrdealColorPool.randomMidnight(random));

        assertEquals(Set.of(OrdealColorPool.GREEN, OrdealColorPool.VIOLET, OrdealColorPool.AMBER), seen);
        assertFalse(OrdealColorPool.supports(OrdealTier.MIDNIGHT, OrdealColorPool.CRIMSON));
    }

    @Test
    void everyScheduledLateFamilyResolvesToARealEntityVariant() {
        for (int color = OrdealColorPool.CRIMSON; color <= OrdealColorPool.AMBER; color++) {
            assertEquals(OrdealColorPool.supports(OrdealTier.DUSK, color),
                    LateOrdealVariant.resolve(OrdealTier.DUSK, color) != null);
            assertEquals(OrdealColorPool.supports(OrdealTier.MIDNIGHT, color),
                    LateOrdealVariant.resolve(OrdealTier.MIDNIGHT, color) != null);
        }
    }

    @Test
    void lateOrdealTrackingSurvivesSaveAndCountsReinforcements() {
        UUID boss = UUID.randomUUID();
        UUID reinforcement = UUID.randomUUID();
        OrdealData data = new OrdealData();
        data.startLateOrdeal(2, OrdealColorPool.AMBER, List.of(
                new OrdealData.LateOrdealRecord(boss, "lobotocraft:company", 1, 2)));
        data.trackLateOrdeal(new OrdealData.LateOrdealRecord(
                reinforcement, "lobotocraft:company", 3, 4));

        OrdealData loaded = OrdealData.load(data.save(new CompoundTag()));

        assertEquals(2, loaded.getLateOrdealTier());
        assertEquals(OrdealColorPool.AMBER, loaded.getLateOrdealColor());
        assertEquals(2, loaded.getTrackedLateOrdeals().size());
        assertEquals(1, loaded.removeTrackedLateOrdeal(boss));
        assertEquals(0, loaded.removeTrackedLateOrdeal(reinforcement));
    }
}
