package com.wzz.lobotocraft.ego;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Global unlock ledger with one independent active user for each equipment type. */
public final class SpecialEgoRegistryData extends SavedData {
    private static final String ID = "lobotocraft_special_ego_registry";

    private final Map<String, Entry> entries = new HashMap<>();

    public static SpecialEgoRegistryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                SpecialEgoRegistryData::load, SpecialEgoRegistryData::new, ID);
    }

    public synchronized boolean unlock(String specialEgoId) {
        if (!validId(specialEgoId)) return false;
        Entry current = entries.get(specialEgoId);
        if (current != null && current.unlocked()) return false;
        entries.put(specialEgoId, current == null
                ? new Entry(true, null, null)
                : new Entry(true, current.weaponUser(), current.armorUser()));
        setDirty();
        return true;
    }

    public synchronized boolean isUnlocked(String specialEgoId) {
        Entry entry = entries.get(specialEgoId);
        return entry != null && entry.unlocked();
    }

    public synchronized AcquireResult acquire(String specialEgoId, String equipmentType,
                                              UUID playerId, String playerName) {
        if (!validId(specialEgoId) || !validType(equipmentType) || playerId == null) {
            return AcquireResult.INVALID;
        }
        Entry entry = entries.get(specialEgoId);
        if (entry == null || !entry.unlocked()) return AcquireResult.LOCKED;

        User current = user(entry, equipmentType);
        String safeName = playerName == null ? "" : playerName;
        if (current != null && !current.playerId().equals(playerId)) {
            return AcquireResult.IN_USE_BY_OTHER;
        }
        User updated = new User(playerId,
                safeName.isBlank() && current != null ? current.playerName() : safeName);
        if (current != null && current.equals(updated)) return AcquireResult.ALREADY_USING;
        entries.put(specialEgoId, withUser(entry, equipmentType, updated));
        setDirty();
        return current == null ? AcquireResult.ACQUIRED : AcquireResult.ALREADY_USING;
    }

    public synchronized boolean release(String specialEgoId, String equipmentType, UUID playerId) {
        if (!validType(equipmentType) || playerId == null) return false;
        Entry entry = entries.get(specialEgoId);
        User current = entry == null ? null : user(entry, equipmentType);
        if (current == null || !current.playerId().equals(playerId)) return false;
        entries.put(specialEgoId, withUser(entry, equipmentType, null));
        setDirty();
        return true;
    }

    public synchronized boolean releaseAll(UUID playerId) {
        if (playerId == null) return false;
        boolean changed = false;
        for (Map.Entry<String, Entry> mapEntry : entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            User weapon = ownedBy(entry.weaponUser(), playerId) ? null : entry.weaponUser();
            User armor = ownedBy(entry.armorUser(), playerId) ? null : entry.armorUser();
            if (weapon != entry.weaponUser() || armor != entry.armorUser()) {
                mapEntry.setValue(new Entry(entry.unlocked(), weapon, armor));
                changed = true;
            }
        }
        if (changed) setDirty();
        return changed;
    }

    public synchronized boolean isUsed(String specialEgoId, String equipmentType) {
        Entry entry = entries.get(specialEgoId);
        return entry != null && user(entry, equipmentType) != null;
    }

    public synchronized boolean isUsedBy(String specialEgoId, String equipmentType, UUID playerId) {
        Entry entry = entries.get(specialEgoId);
        User user = entry == null ? null : user(entry, equipmentType);
        return user != null && user.playerId().equals(playerId);
    }

    public synchronized UUID userId(String specialEgoId, String equipmentType) {
        Entry entry = entries.get(specialEgoId);
        User user = entry == null ? null : user(entry, equipmentType);
        return user == null ? null : user.playerId();
    }

    public synchronized String userName(String specialEgoId, String equipmentType) {
        Entry entry = entries.get(specialEgoId);
        User user = entry == null ? null : user(entry, equipmentType);
        return user == null ? "" : user.playerName();
    }

    public static SpecialEgoRegistryData load(CompoundTag tag) {
        SpecialEgoRegistryData data = new SpecialEgoRegistryData();
        ListTag savedEntries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < savedEntries.size(); index++) {
            CompoundTag saved = savedEntries.getCompound(index);
            String id = saved.getString("Id");
            if (!validId(id)) continue;

            // 0.1.13 pre-release migration: the old format stored one permanent owner
            // and one indivisible equipped flag. An active old set occupies both new slots.
            if (saved.hasUUID("Owner")) {
                User legacy = new User(saved.getUUID("Owner"), saved.getString("OwnerName"));
                boolean equipped = saved.getBoolean("Equipped");
                data.entries.put(id, new Entry(true,
                        equipped ? legacy : null, equipped ? legacy : null));
                continue;
            }

            boolean unlocked = saved.getBoolean("Unlocked");
            User weapon = readUser(saved, "Weapon");
            User armor = readUser(saved, "Armor");
            if (unlocked || weapon != null || armor != null) {
                data.entries.put(id, new Entry(true, weapon, armor));
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag savedEntries = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag saved = new CompoundTag();
            saved.putString("Id", id);
            saved.putBoolean("Unlocked", entry.unlocked());
            writeUser(saved, "Weapon", entry.weaponUser());
            writeUser(saved, "Armor", entry.armorUser());
            savedEntries.add(saved);
        });
        tag.put("Entries", savedEntries);
        return tag;
    }

    private static User user(Entry entry, String equipmentType) {
        return "weapon".equals(equipmentType) ? entry.weaponUser() : entry.armorUser();
    }

    private static Entry withUser(Entry entry, String equipmentType, User user) {
        return "weapon".equals(equipmentType)
                ? new Entry(entry.unlocked(), user, entry.armorUser())
                : new Entry(entry.unlocked(), entry.weaponUser(), user);
    }

    private static boolean ownedBy(User user, UUID playerId) {
        return user != null && user.playerId().equals(playerId);
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= 64;
    }

    public static boolean validType(String equipmentType) {
        return "weapon".equals(equipmentType) || "armor".equals(equipmentType);
    }

    private static User readUser(CompoundTag tag, String prefix) {
        return tag.hasUUID(prefix + "User")
                ? new User(tag.getUUID(prefix + "User"), tag.getString(prefix + "UserName"))
                : null;
    }

    private static void writeUser(CompoundTag tag, String prefix, User user) {
        if (user == null) return;
        tag.putUUID(prefix + "User", user.playerId());
        tag.putString(prefix + "UserName", user.playerName());
    }

    public enum AcquireResult {
        ACQUIRED,
        ALREADY_USING,
        IN_USE_BY_OTHER,
        LOCKED,
        INVALID
    }

    private record Entry(boolean unlocked, User weaponUser, User armorUser) {
    }

    private record User(UUID playerId, String playerName) {
    }
}
