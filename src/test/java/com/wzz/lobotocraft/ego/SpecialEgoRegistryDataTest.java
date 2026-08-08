package com.wzz.lobotocraft.ego;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialEgoRegistryDataTest {
    @Test
    void weaponAndArmorMayBeUsedByDifferentPlayers() {
        SpecialEgoRegistryData data = new SpecialEgoRegistryData();
        UUID weaponUser = UUID.randomUUID();
        UUID armorUser = UUID.randomUUID();

        assertEquals(SpecialEgoRegistryData.AcquireResult.LOCKED,
                data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", weaponUser, "Weapon"));
        assertTrue(data.unlock(SpecialEgoCatalog.THIN_DUSK_ID));
        assertEquals(SpecialEgoRegistryData.AcquireResult.ACQUIRED,
                data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", weaponUser, "Weapon"));
        assertEquals(SpecialEgoRegistryData.AcquireResult.ACQUIRED,
                data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "armor", armorUser, "Armor"));

        assertTrue(data.isUsedBy(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", weaponUser));
        assertTrue(data.isUsedBy(SpecialEgoCatalog.THIN_DUSK_ID, "armor", armorUser));
        assertFalse(data.isUsedBy(SpecialEgoCatalog.THIN_DUSK_ID, "armor", weaponUser));
    }

    @Test
    void sameTypeRejectsSecondPlayerUntilReleased() {
        SpecialEgoRegistryData data = new SpecialEgoRegistryData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.unlock(SpecialEgoCatalog.THIN_DUSK_ID);
        data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", first, "First");

        assertEquals(SpecialEgoRegistryData.AcquireResult.IN_USE_BY_OTHER,
                data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", second, "Second"));
        assertTrue(data.release(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", first));
        assertEquals(SpecialEgoRegistryData.AcquireResult.ACQUIRED,
                data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", second, "Second"));
    }

    @Test
    void newFormatPersistsIndependentSlots() {
        SpecialEgoRegistryData data = new SpecialEgoRegistryData();
        UUID weapon = UUID.randomUUID();
        UUID armor = UUID.randomUUID();
        data.unlock(SpecialEgoCatalog.THIN_DUSK_ID);
        data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", weapon, "Weapon");
        data.acquire(SpecialEgoCatalog.THIN_DUSK_ID, "armor", armor, "Armor");

        SpecialEgoRegistryData loaded = SpecialEgoRegistryData.load(data.save(new CompoundTag()));
        assertTrue(loaded.isUnlocked(SpecialEgoCatalog.THIN_DUSK_ID));
        assertEquals("Weapon", loaded.userName(SpecialEgoCatalog.THIN_DUSK_ID, "weapon"));
        assertEquals("Armor", loaded.userName(SpecialEgoCatalog.THIN_DUSK_ID, "armor"));
    }

    @Test
    void legacyEquippedOwnerMigratesToBothSlots() {
        UUID owner = UUID.randomUUID();
        CompoundTag legacyEntry = new CompoundTag();
        legacyEntry.putString("Id", SpecialEgoCatalog.THIN_DUSK_ID);
        legacyEntry.putUUID("Owner", owner);
        legacyEntry.putString("OwnerName", "Legacy");
        legacyEntry.putBoolean("Equipped", true);
        ListTag entries = new ListTag();
        entries.add(legacyEntry);
        CompoundTag root = new CompoundTag();
        root.put("Entries", entries);

        SpecialEgoRegistryData loaded = SpecialEgoRegistryData.load(root);
        assertTrue(loaded.isUnlocked(SpecialEgoCatalog.THIN_DUSK_ID));
        assertTrue(loaded.isUsedBy(SpecialEgoCatalog.THIN_DUSK_ID, "weapon", owner));
        assertTrue(loaded.isUsedBy(SpecialEgoCatalog.THIN_DUSK_ID, "armor", owner));
    }
}
