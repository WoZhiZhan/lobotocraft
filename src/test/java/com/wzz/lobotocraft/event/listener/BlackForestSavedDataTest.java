package com.wzz.lobotocraft.event.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class BlackForestSavedDataTest {
    @Test
    void legacyStringListSurvivesMigrationWithoutBeingDeclaredDead() {
        String birdId = UUID.randomUUID().toString();
        CompoundTag legacy = new CompoundTag();
        ListTag birds = new ListTag();
        birds.add(StringTag.valueOf(birdId));
        legacy.put("EscapedBirds", birds);
        legacy.putBoolean("DoorSpawned", true);

        BlackForestEvent.BlackForestSavedData data =
                BlackForestEvent.BlackForestSavedData.load(legacy);
        assertTrue(data.getEscapedBirdUUIDs().contains(birdId));
        assertTrue(data.isDoorSpawned());

        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(1, saved.getList("EscapedBirds", Tag.TAG_STRING).size());
        assertEquals(0, saved.getList("EscapedBirdRecords", Tag.TAG_COMPOUND).size());
    }

    @Test
    void pendingCapturedBirdRecordsRoundTripUntilRestorationSucceeds() {
        CompoundTag record = new CompoundTag();
        record.putString("Code", "large_bird");
        record.putString("Dimension", "lobotocraft:loboto");
        record.putInt("X", 10);
        record.putInt("Y", 64);
        record.putInt("Z", -4);
        record.putString("HomeRoomDimension", "lobotocraft:loboto");
        record.putLong("HomeRoomMarker", net.minecraft.core.BlockPos.asLong(12, 63, -4));
        BlackForestEvent.BlackForestSavedData data = new BlackForestEvent.BlackForestSavedData();
        data.queueCapturedBird(record);

        CompoundTag saved = data.save(new CompoundTag());
        BlackForestEvent.BlackForestSavedData loaded =
                BlackForestEvent.BlackForestSavedData.load(saved);
        CompoundTag savedAgain = loaded.save(new CompoundTag());
        assertEquals(1, savedAgain.getList("PendingCapturedBirds", Tag.TAG_COMPOUND).size());
        assertEquals("large_bird", savedAgain.getList(
                "PendingCapturedBirds", Tag.TAG_COMPOUND).getCompound(0).getString("Code"));
        assertEquals("lobotocraft:loboto", savedAgain.getList(
                "PendingCapturedBirds", Tag.TAG_COMPOUND).getCompound(0).getString("HomeRoomDimension"));
        assertTrue(savedAgain.getList("PendingCapturedBirds", Tag.TAG_COMPOUND)
                .getCompound(0).contains("HomeRoomMarker"));
    }

    @Test
    void capturedBirdRetryQueueDeduplicatesByOriginalEntityUuid() {
        UUID birdId = UUID.randomUUID();
        CompoundTag first = capturedRecord("large_bird", birdId, 10);
        CompoundTag duplicate = capturedRecord("large_bird", birdId, 40);
        CompoundTag other = capturedRecord("punishing_bird", UUID.randomUUID(), 10);

        BlackForestEvent.BlackForestSavedData data = new BlackForestEvent.BlackForestSavedData();
        data.queueCapturedBirds(java.util.List.of(first, duplicate, other));

        ListTag pending = data.save(new CompoundTag())
                .getList("PendingCapturedBirds", Tag.TAG_COMPOUND);
        assertEquals(2, pending.size());
        assertEquals(birdId, pending.getCompound(0).getCompound("Entity").getUUID("UUID"));
    }

    @Test
    void capturedBirdSnapshotIsSanitizedBeforeItCanReenterTheWorld() {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("HasEscaped", true);
        snapshot.putBoolean("PendingContainmentReturn", true);
        snapshot.putBoolean("PendingStopEscapeEvent", true);
        snapshot.putBoolean("ContainmentReturnWasSuppression", true);
        snapshot.putBoolean("Invulnerable", true);
        snapshot.putBoolean("NoAI", true);
        snapshot.putInt("EscapePosX", 4);
        snapshot.putString("EscapeDimension", "lobotocraft:loboto");
        snapshot.putBoolean("Angry", true);
        snapshot.putBoolean("Returning", true);
        snapshot.putString("CurrentAction", "big_bite");
        snapshot.putUUID("AngryTarget", UUID.randomUUID());
        CompoundTag forgeData = new CompoundTag();
        forgeData.putInt("EscapeTime", 500);
        forgeData.putInt("NotFoundTick", 99);
        forgeData.putString("KeepMe", "yes");
        snapshot.put("ForgeData", forgeData);

        BlackForestEvent.sanitizeCapturedBirdSnapshot(snapshot, "punishing_bird");

        assertFalse(snapshot.getBoolean("HasEscaped"));
        assertFalse(snapshot.getBoolean("PendingContainmentReturn"));
        assertFalse(snapshot.getBoolean("PendingStopEscapeEvent"));
        assertFalse(snapshot.getBoolean("ContainmentReturnWasSuppression"));
        assertFalse(snapshot.getBoolean("Invulnerable"));
        assertFalse(snapshot.getBoolean("NoAI"));
        assertFalse(snapshot.contains("EscapePosX"));
        assertFalse(snapshot.contains("EscapeDimension"));
        assertFalse(snapshot.getBoolean("Angry"));
        assertFalse(snapshot.getBoolean("Returning"));
        assertEquals("idle", snapshot.getString("CurrentAction"));
        assertFalse(snapshot.hasUUID("AngryTarget"));
        assertFalse(snapshot.getCompound("ForgeData").contains("EscapeTime"));
        assertFalse(snapshot.getCompound("ForgeData").contains("NotFoundTick"));
        assertEquals("yes", snapshot.getCompound("ForgeData").getString("KeepMe"));
        assertEquals(3, snapshot.getList("Motion", Tag.TAG_DOUBLE).size());
    }

    private static CompoundTag capturedRecord(String code, UUID uuid, int x) {
        CompoundTag record = new CompoundTag();
        record.putString("Code", code);
        record.putString("Dimension", "lobotocraft:loboto");
        record.putInt("X", x);
        record.putInt("Y", 64);
        record.putInt("Z", 0);
        CompoundTag entity = new CompoundTag();
        entity.putUUID("UUID", uuid);
        record.put("Entity", entity);
        return record;
    }
}
