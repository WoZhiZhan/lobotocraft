package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndBirdEggTrackerTest {
    @Test
    void encounterRequiresExactlyOneEggOfEachType() {
        EndBirdEggTracker tracker = new EndBirdEggTracker();
        UUID endBird = UUID.randomUUID();
        assertFalse(tracker.beginEncounter(endBird, Map.of(
                UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL,
                UUID.randomUUID(), EndBirdEggTracker.EGG_HIGH)));
        assertFalse(tracker.beginEncounter(endBird, Map.of(
                UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL,
                UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL,
                UUID.randomUUID(), EndBirdEggTracker.EGG_EYE)));
        assertFalse(tracker.hasActiveEncounter());
    }

    @Test
    void foreignManualEggWrongTypeAndDuplicateCallbacksNeverAdvance() {
        Fixture fixture = fixture();
        EndBirdEggTracker tracker = fixture.tracker();
        assertEquals(EndBirdEggTracker.EggDestructionResult.UNKNOWN_EGG,
                tracker.markEggDestroyed(UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL));
        assertEquals(EndBirdEggTracker.EggDestructionResult.WRONG_TYPE,
                tracker.markEggDestroyed(fixture.small(), EndBirdEggTracker.EGG_EYE));
        assertEquals(0, tracker.getDestroyedEggCount());

        assertEquals(EndBirdEggTracker.EggDestructionResult.ACCEPTED,
                tracker.markEggDestroyed(fixture.small(), EndBirdEggTracker.EGG_SMALL));
        assertEquals(EndBirdEggTracker.EggDestructionResult.DUPLICATE,
                tracker.markEggDestroyed(fixture.small(), EndBirdEggTracker.EGG_SMALL));
        assertEquals(1, tracker.getDestroyedEggCount());
        assertFalse(tracker.areAllEggsDestroyed());
        assertFalse(tracker.canEndBirdDie(fixture.endBird()));
    }

    @Test
    void everyDestructionOrderRequiresThreeDistinctRegisteredEggs() {
        List<List<String>> orders = List.of(
                List.of("small", "high", "eye"), List.of("small", "eye", "high"),
                List.of("high", "small", "eye"), List.of("high", "eye", "small"),
                List.of("eye", "small", "high"), List.of("eye", "high", "small"));
        for (List<String> order : orders) {
            Fixture fixture = fixture();
            for (int i = 0; i < order.size(); i++) {
                String type = order.get(i);
                EndBirdEggTracker.EggDestructionResult result = fixture.tracker()
                        .markEggDestroyed(fixture.uuid(type), type);
                assertEquals(i == 2 ? EndBirdEggTracker.EggDestructionResult.COMPLETE
                                : EndBirdEggTracker.EggDestructionResult.ACCEPTED,
                        result, order.toString());
                assertEquals(i + 1, fixture.tracker().getDestroyedEggCount());
            }
            assertTrue(fixture.tracker().areAllEggsDestroyed());
            assertTrue(fixture.tracker().canEndBirdDie(fixture.endBird()));
        }
    }

    @Test
    void activeEncounterRoundTripsWithoutAcceptingOldEventEggs() {
        Fixture old = fixture();
        old.tracker().markEggDestroyed(old.small(), EndBirdEggTracker.EGG_SMALL);
        CompoundTag saved = old.tracker().save(new CompoundTag());
        EndBirdEggTracker loaded = EndBirdEggTracker.load(saved);
        assertTrue(loaded.hasActiveEncounter());
        assertEquals(1, loaded.getDestroyedEggCount());
        assertEquals(EndBirdEggTracker.EggDestructionResult.DUPLICATE,
                loaded.markEggDestroyed(old.small(), EndBirdEggTracker.EGG_SMALL));
        assertEquals(EndBirdEggTracker.EggDestructionResult.UNKNOWN_EGG,
                loaded.markEggDestroyed(UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL));
    }

    @Test
    void validLegacyEncounterMigratesButPollutedLegacyDataIsCleared() {
        Fixture source = fixture();
        CompoundTag legacy = source.tracker().save(new CompoundTag());
        legacy.remove("EncounterUUID");
        EndBirdEggTracker migrated = EndBirdEggTracker.load(legacy);
        assertTrue(migrated.hasActiveEncounter());
        assertFalse(migrated.canEndBirdDie(source.endBird()));

        CompoundTag polluted = new CompoundTag();
        polluted.putUUID("EndBirdUUID", UUID.randomUUID());
        ListTag eggs = new ListTag();
        eggs.add(eggTag(UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL));
        eggs.add(eggTag(UUID.randomUUID(), EndBirdEggTracker.EGG_SMALL));
        eggs.add(eggTag(UUID.randomUUID(), EndBirdEggTracker.EGG_EYE));
        polluted.put("EggTypes", eggs);
        ListTag destroyed = new ListTag();
        destroyed.add(StringTag.valueOf(UUID.randomUUID().toString()));
        polluted.put("DestroyedEggs", destroyed);

        EndBirdEggTracker cleaned = EndBirdEggTracker.load(polluted);
        assertFalse(cleaned.hasActiveEncounter());
        assertNull(cleaned.getEndBirdUUID());
        assertEquals(0, cleaned.getDestroyedEggCount());
        assertEquals(0, cleaned.save(new CompoundTag()).getList("EggTypes", Tag.TAG_COMPOUND).size());
    }

    @Test
    void pendingCompletionAndKillerSurviveSaveReload() {
        Fixture fixture = fixture();
        fixture.tracker().markEggDestroyed(fixture.small(), EndBirdEggTracker.EGG_SMALL);
        fixture.tracker().markEggDestroyed(fixture.high(), EndBirdEggTracker.EGG_HIGH);
        fixture.tracker().markEggDestroyed(fixture.eye(), EndBirdEggTracker.EGG_EYE);
        UUID killer = UUID.randomUUID();
        fixture.tracker().recordCompletionKiller(killer);

        EndBirdEggTracker loaded = EndBirdEggTracker.load(
                fixture.tracker().save(new CompoundTag()));

        assertTrue(loaded.isCompletionPending(fixture.endBird()));
        assertTrue(loaded.canEndBirdDie(fixture.endBird()));
        assertEquals(killer, loaded.getCompletionKillerUUID());
    }

    @Test
    void forceAbortKeepsCleanupWorkButEndsEncounterAuthority() {
        Fixture fixture = fixture();
        UUID charmedPlayer = UUID.randomUUID();

        List<UUID> eggs = fixture.tracker().abortEncounter(
                fixture.endBird(), List.of(charmedPlayer));

        assertFalse(fixture.tracker().hasActiveEncounter());
        assertEquals(3, eggs.size());
        eggs.forEach(egg -> assertTrue(fixture.tracker().needsEggCleanup(egg)));
        assertTrue(fixture.tracker().needsCharmCleanup(charmedPlayer));
        assertEquals(EndBirdEggTracker.EggDestructionResult.NO_ENCOUNTER,
                fixture.tracker().markEggDestroyed(fixture.small(), EndBirdEggTracker.EGG_SMALL));

        fixture.tracker().acknowledgeEggCleanup(fixture.small());
        fixture.tracker().acknowledgeCharmCleanup(charmedPlayer);
        assertFalse(fixture.tracker().needsEggCleanup(fixture.small()));
        assertFalse(fixture.tracker().needsCharmCleanup(charmedPlayer));
    }

    @Test
    void forceAbortCleanupQueuesSurviveRestartUntilAcknowledged() {
        Fixture fixture = fixture();
        UUID charmedPlayer = UUID.randomUUID();
        fixture.tracker().abortEncounter(fixture.endBird(), List.of(charmedPlayer));

        EndBirdEggTracker loaded = EndBirdEggTracker.load(
                fixture.tracker().save(new CompoundTag()));

        assertFalse(loaded.hasActiveEncounter());
        assertTrue(loaded.needsEggCleanup(fixture.small()));
        assertTrue(loaded.needsEggCleanup(fixture.high()));
        assertTrue(loaded.needsEggCleanup(fixture.eye()));
        assertTrue(loaded.needsCharmCleanup(charmedPlayer));
    }

    @Test
    void legacyDimensionRecordMovesOnceIntoCanonicalTracker() {
        Fixture legacyFixture = fixture();
        legacyFixture.tracker().markEggDestroyed(
                legacyFixture.small(), EndBirdEggTracker.EGG_SMALL);
        EndBirdEggTracker canonical = new EndBirdEggTracker();

        canonical.mergeLegacy(legacyFixture.tracker());

        assertTrue(canonical.hasActiveEncounter());
        assertEquals(legacyFixture.endBird(), canonical.getEndBirdUUID());
        assertEquals(1, canonical.getDestroyedEggCount());
        assertFalse(legacyFixture.tracker().hasActiveEncounter());
        canonical.mergeLegacy(legacyFixture.tracker());
        assertEquals(1, canonical.getDestroyedEggCount());
    }

    @Test
    void conflictingLegacyDimensionEncounterIsQueuedForSafeAbort() {
        Fixture canonical = fixture();
        Fixture conflicting = fixture();

        canonical.tracker().mergeLegacy(conflicting.tracker());

        assertEquals(canonical.endBird(), canonical.tracker().getEndBirdUUID());
        assertTrue(canonical.tracker().needsEndBirdAbort(conflicting.endBird()));
        assertTrue(canonical.tracker().needsEggCleanup(conflicting.small()));
        assertTrue(canonical.tracker().needsEggCleanup(conflicting.high()));
        assertTrue(canonical.tracker().needsEggCleanup(conflicting.eye()));
        assertFalse(conflicting.tracker().hasActiveEncounter());
    }

    private static CompoundTag eggTag(UUID uuid, String type) {
        CompoundTag tag = new CompoundTag();
        tag.putString("UUID", uuid.toString());
        tag.putString("Type", type);
        return tag;
    }

    private static Fixture fixture() {
        UUID endBird = UUID.randomUUID();
        UUID small = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        UUID eye = UUID.randomUUID();
        EndBirdEggTracker tracker = new EndBirdEggTracker();
        Map<UUID, String> eggs = new LinkedHashMap<>();
        eggs.put(small, EndBirdEggTracker.EGG_SMALL);
        eggs.put(high, EndBirdEggTracker.EGG_HIGH);
        eggs.put(eye, EndBirdEggTracker.EGG_EYE);
        assertTrue(tracker.beginEncounter(endBird, eggs));
        return new Fixture(tracker, endBird, small, high, eye);
    }

    private record Fixture(EndBirdEggTracker tracker, UUID endBird, UUID small, UUID high, UUID eye) {
        UUID uuid(String type) {
            return switch (type) {
                case EndBirdEggTracker.EGG_SMALL -> small;
                case EndBirdEggTracker.EGG_HIGH -> high;
                case EndBirdEggTracker.EGG_EYE -> eye;
                default -> throw new IllegalArgumentException(type);
            };
        }
    }
}
