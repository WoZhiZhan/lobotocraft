package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persists one atomic Apocalypse Bird encounter and its exact three event eggs. */
public class EndBirdEggTracker extends SavedData {
    private static final String NAME = "end_bird_egg_tracker";
    private static final int MAX_PENDING_EGG_CLEANUPS = 64;
    private static final int MAX_PENDING_CHARM_CLEANUPS = 4096;
    private static final int MAX_PENDING_END_BIRD_ABORTS = 16;

    public static final String EGG_SMALL = "small";
    public static final String EGG_HIGH = "high";
    public static final String EGG_EYE = "eye";
    private static final Set<String> REQUIRED_TYPES = Set.of(EGG_SMALL, EGG_HIGH, EGG_EYE);

    private final Map<String, String> eggTypes = new HashMap<>();
    private final Set<String> destroyedEggs = new HashSet<>();
    private final Set<String> pendingEggCleanup = new HashSet<>();
    private final Set<String> pendingCharmCleanup = new HashSet<>();
    private final Set<String> pendingEndBirdAbort = new HashSet<>();
    private UUID encounterUUID;
    private UUID endBirdUUID;
    private UUID completionKillerUUID;
    private String completionEggType;
    private boolean completionPending;

    public enum EggDestructionResult {
        NO_ENCOUNTER, UNKNOWN_EGG, WRONG_TYPE, DUPLICATE, ACCEPTED, COMPLETE
    }

    /**
     * All dimensions share the overworld record. A valid old per-dimension record is imported
     * once when an entity from that dimension first asks for the tracker.
     */
    public static EndBirdEggTracker get(ServerLevel level) {
        ServerLevel canonicalLevel = level.getServer().overworld();
        EndBirdEggTracker canonical = getLocal(canonicalLevel);
        if (level != canonicalLevel) {
            EndBirdEggTracker legacy = getLocal(level);
            if (legacy != canonical) canonical.mergeLegacy(legacy);
        }
        return canonical;
    }

    private static EndBirdEggTracker getLocal(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                EndBirdEggTracker::load, EndBirdEggTracker::new, NAME);
    }

    /**
     * Starts an encounter only when there is exactly one UUID for every required egg type.
     * Manually spawned eggs are deliberately not auto-registered.
     */
    public boolean beginEncounter(UUID endBird, Map<UUID, String> eggs) {
        if (endBird == null || encounterUUID != null || !isCompleteEggSet(eggs)) return false;
        encounterUUID = UUID.randomUUID();
        endBirdUUID = endBird;
        completionKillerUUID = null;
        completionEggType = null;
        completionPending = false;
        eggTypes.clear();
        destroyedEggs.clear();
        eggs.forEach((uuid, type) -> eggTypes.put(uuid.toString(), type));
        setDirty();
        return true;
    }

    static boolean isCompleteEggSet(Map<UUID, String> eggs) {
        if (eggs == null || eggs.size() != REQUIRED_TYPES.size()
                || eggs.keySet().stream().anyMatch(java.util.Objects::isNull)) return false;
        Set<String> types = new HashSet<>(eggs.values());
        return !types.contains(null) && types.equals(REQUIRED_TYPES);
    }

    public boolean hasActiveEncounter() {
        return encounterUUID != null && endBirdUUID != null && hasExactlyThreeRequiredEggs();
    }

    public boolean isTrackedEndBird(UUID uuid) {
        return hasActiveEncounter() && endBirdUUID.equals(uuid);
    }

    public boolean isTrackedEgg(UUID uuid, String expectedType) {
        return hasActiveEncounter() && uuid != null && expectedType != null
                && expectedType.equals(eggTypes.get(uuid.toString()));
    }

    public UUID getEggUUID(String type) {
        if (!REQUIRED_TYPES.contains(type)) return null;
        for (Map.Entry<String, String> entry : eggTypes.entrySet()) {
            if (type.equals(entry.getValue())) return parseUUID(entry.getKey());
        }
        return null;
    }

    public List<UUID> getTrackedEggUUIDs() {
        List<UUID> result = new ArrayList<>();
        eggTypes.keySet().forEach(value -> {
            UUID uuid = parseUUID(value);
            if (uuid != null) result.add(uuid);
        });
        return Collections.unmodifiableList(result);
    }

    public EggDestructionResult markEggDestroyed(UUID eggUUID, String expectedType) {
        if (!hasActiveEncounter()) return EggDestructionResult.NO_ENCOUNTER;
        if (eggUUID == null || !eggTypes.containsKey(eggUUID.toString())) {
            return EggDestructionResult.UNKNOWN_EGG;
        }
        String registeredType = eggTypes.get(eggUUID.toString());
        if (!registeredType.equals(expectedType)) return EggDestructionResult.WRONG_TYPE;
        if (!destroyedEggs.add(eggUUID.toString())) return EggDestructionResult.DUPLICATE;
        if (areAllEggsDestroyed()) {
            completionPending = true;
            completionEggType = registeredType;
        }
        setDirty();
        return completionPending ? EggDestructionResult.COMPLETE : EggDestructionResult.ACCEPTED;
    }

    public int getDestroyedEggCount() {
        return (int) destroyedEggs.stream().filter(eggTypes::containsKey).count();
    }

    public int getAliveEggCount() {
        return Math.max(0, eggTypes.size() - getDestroyedEggCount());
    }

    public List<String> getDestroyedEggTypes() {
        List<String> types = new ArrayList<>();
        destroyedEggs.forEach(uuid -> {
            String type = eggTypes.get(uuid);
            if (type != null) types.add(type);
        });
        return Collections.unmodifiableList(types);
    }

    public boolean areAllEggsDestroyed() {
        return hasExactlyThreeRequiredEggs() && getDestroyedEggCount() == REQUIRED_TYPES.size();
    }

    public boolean canEndBirdDie(UUID uuid) {
        return !isTrackedEndBird(uuid) || areAllEggsDestroyed();
    }

    public boolean isCompletionPending(UUID uuid) {
        return completionPending && isTrackedEndBird(uuid) && areAllEggsDestroyed();
    }

    public void recordCompletionKiller(UUID playerUUID) {
        if (!completionPending || playerUUID == null) return;
        completionKillerUUID = playerUUID;
        setDirty();
    }

    public UUID getCompletionKillerUUID() {
        return completionKillerUUID;
    }

    public String getCompletionEggType() {
        return completionEggType;
    }

    public UUID getEndBirdUUID() {
        return endBirdUUID;
    }

    public void clearIfEndBird(UUID uuid) {
        if (isTrackedEndBird(uuid)) clearEncounter();
    }

    /**
     * Aborts a force-removed encounter. Loaded entities can be cleaned immediately; UUIDs left in
     * these queues are removed when their entity/player is next loaded.
     */
    public List<UUID> abortEncounter(UUID uuid, Collection<UUID> charmedPlayers) {
        if (!isTrackedEndBird(uuid)) return List.of();
        List<UUID> eggs = new ArrayList<>(getTrackedEggUUIDs());
        eggs.forEach(egg -> addBounded(pendingEggCleanup, egg, MAX_PENDING_EGG_CLEANUPS));
        queueCharmCleanup(charmedPlayers);
        clearEncounter();
        return Collections.unmodifiableList(eggs);
    }

    public void queueCharmCleanup(Collection<UUID> playerUUIDs) {
        if (playerUUIDs == null) return;
        boolean changed = false;
        for (UUID uuid : playerUUIDs) {
            if (uuid != null && pendingCharmCleanup.size() < MAX_PENDING_CHARM_CLEANUPS) {
                changed |= pendingCharmCleanup.add(uuid.toString());
            }
        }
        if (changed) setDirty();
    }

    public boolean needsEggCleanup(UUID uuid) {
        return uuid != null && pendingEggCleanup.contains(uuid.toString());
    }

    public void acknowledgeEggCleanup(UUID uuid) {
        if (uuid != null && pendingEggCleanup.remove(uuid.toString())) setDirty();
    }

    public boolean needsCharmCleanup(UUID uuid) {
        return uuid != null && pendingCharmCleanup.contains(uuid.toString());
    }

    public void acknowledgeCharmCleanup(UUID uuid) {
        if (uuid != null && pendingCharmCleanup.remove(uuid.toString())) setDirty();
    }

    public boolean needsEndBirdAbort(UUID uuid) {
        return uuid != null && pendingEndBirdAbort.contains(uuid.toString());
    }

    public void acknowledgeEndBirdAbort(UUID uuid) {
        if (uuid != null && pendingEndBirdAbort.remove(uuid.toString())) setDirty();
    }

    public void clear() {
        clearEncounter();
        pendingEggCleanup.clear();
        pendingCharmCleanup.clear();
        pendingEndBirdAbort.clear();
        setDirty();
    }

    private void clearEncounter() {
        eggTypes.clear();
        destroyedEggs.clear();
        encounterUUID = null;
        endBirdUUID = null;
        completionKillerUUID = null;
        completionEggType = null;
        completionPending = false;
        setDirty();
    }

    private boolean hasExactlyThreeRequiredEggs() {
        if (eggTypes.size() != REQUIRED_TYPES.size()) return false;
        return new HashSet<>(eggTypes.values()).equals(REQUIRED_TYPES);
    }

    /** Package-visible for migration regression tests. */
    void mergeLegacy(EndBirdEggTracker legacy) {
        if (legacy == null || legacy == this) return;
        if (!legacy.hasActiveEncounter() && legacy.pendingEggCleanup.isEmpty()
                && legacy.pendingCharmCleanup.isEmpty() && legacy.pendingEndBirdAbort.isEmpty()) return;
        boolean changed = false;
        for (String uuid : legacy.pendingEggCleanup) {
            if (pendingEggCleanup.size() >= MAX_PENDING_EGG_CLEANUPS) break;
            changed |= pendingEggCleanup.add(uuid);
        }
        for (String uuid : legacy.pendingCharmCleanup) {
            if (pendingCharmCleanup.size() >= MAX_PENDING_CHARM_CLEANUPS) break;
            changed |= pendingCharmCleanup.add(uuid);
        }
        for (String uuid : legacy.pendingEndBirdAbort) {
            if (pendingEndBirdAbort.size() >= MAX_PENDING_END_BIRD_ABORTS) break;
            changed |= pendingEndBirdAbort.add(uuid);
        }

        if (!hasActiveEncounter() && legacy.hasActiveEncounter()) {
            encounterUUID = legacy.encounterUUID;
            endBirdUUID = legacy.endBirdUUID;
            eggTypes.clear();
            eggTypes.putAll(legacy.eggTypes);
            destroyedEggs.clear();
            destroyedEggs.addAll(legacy.destroyedEggs);
            completionPending = legacy.completionPending || areAllEggsDestroyed();
            completionKillerUUID = legacy.completionKillerUUID;
            completionEggType = legacy.completionEggType;
            changed = true;
        } else if (sameEncounter(legacy)) {
            changed |= destroyedEggs.addAll(legacy.destroyedEggs);
            boolean mergedCompletion = completionPending || legacy.completionPending || areAllEggsDestroyed();
            if (mergedCompletion != completionPending) changed = true;
            completionPending = mergedCompletion;
            if (completionKillerUUID == null && legacy.completionKillerUUID != null) {
                completionKillerUUID = legacy.completionKillerUUID;
                changed = true;
            }
            if (completionEggType == null && legacy.completionEggType != null) {
                completionEggType = legacy.completionEggType;
                changed = true;
            }
        } else if (legacy.hasActiveEncounter()) {
            // Old versions could create one tracker per dimension. The canonical record wins;
            // a conflicting secondary encounter is safely aborted as its entities load.
            for (UUID egg : legacy.getTrackedEggUUIDs()) {
                if (pendingEggCleanup.size() >= MAX_PENDING_EGG_CLEANUPS) break;
                changed |= pendingEggCleanup.add(egg.toString());
            }
            if (pendingEndBirdAbort.size() < MAX_PENDING_END_BIRD_ABORTS) {
                changed |= pendingEndBirdAbort.add(legacy.endBirdUUID.toString());
            }
        }

        if (changed) setDirty();
        // The overworld record is authoritative after migration. Clearing the local copy prevents
        // the same legacy encounter from being imported again after the canonical one completes.
        legacy.clear();
    }

    private boolean sameEncounter(EndBirdEggTracker other) {
        return hasActiveEncounter() && other.hasActiveEncounter()
                && endBirdUUID.equals(other.endBirdUUID) && eggTypes.equals(other.eggTypes);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (encounterUUID != null) tag.putUUID("EncounterUUID", encounterUUID);
        if (endBirdUUID != null) tag.putUUID("EndBirdUUID", endBirdUUID);
        if (completionKillerUUID != null) tag.putUUID("CompletionKillerUUID", completionKillerUUID);
        if (completionEggType != null) tag.putString("CompletionEggType", completionEggType);
        tag.putBoolean("CompletionPending", completionPending);

        ListTag eggList = new ListTag();
        eggTypes.forEach((uuid, type) -> {
            CompoundTag eggTag = new CompoundTag();
            eggTag.putString("UUID", uuid);
            eggTag.putString("Type", type);
            eggList.add(eggTag);
        });
        tag.put("EggTypes", eggList);
        tag.put("DestroyedEggs", uuidList(destroyedEggs));
        tag.put("PendingEggCleanup", uuidList(pendingEggCleanup));
        tag.put("PendingCharmCleanup", uuidList(pendingCharmCleanup));
        tag.put("PendingEndBirdAbort", uuidList(pendingEndBirdAbort));
        return tag;
    }

    public static EndBirdEggTracker load(CompoundTag tag) {
        EndBirdEggTracker tracker = new EndBirdEggTracker();
        if (tag.hasUUID("EndBirdUUID")) tracker.endBirdUUID = tag.getUUID("EndBirdUUID");
        if (tag.hasUUID("CompletionKillerUUID")) {
            tracker.completionKillerUUID = tag.getUUID("CompletionKillerUUID");
        }
        String completionEggType = tag.getString("CompletionEggType");
        if (REQUIRED_TYPES.contains(completionEggType)) {
            tracker.completionEggType = completionEggType;
        }

        ListTag eggList = tag.getList("EggTypes", Tag.TAG_COMPOUND);
        for (int i = 0; i < eggList.size(); i++) {
            CompoundTag eggTag = eggList.getCompound(i);
            String uuid = eggTag.getString("UUID");
            String type = eggTag.getString("Type");
            if (parseUUID(uuid) != null && REQUIRED_TYPES.contains(type)) tracker.eggTypes.put(uuid, type);
        }
        readUUIDSet(tag, "DestroyedEggs", tracker.destroyedEggs, REQUIRED_TYPES.size());
        readUUIDSet(tag, "PendingEggCleanup", tracker.pendingEggCleanup, MAX_PENDING_EGG_CLEANUPS);
        readUUIDSet(tag, "PendingCharmCleanup", tracker.pendingCharmCleanup, MAX_PENDING_CHARM_CLEANUPS);
        readUUIDSet(tag, "PendingEndBirdAbort", tracker.pendingEndBirdAbort, MAX_PENDING_END_BIRD_ABORTS);
        tracker.destroyedEggs.removeIf(uuid -> !tracker.eggTypes.containsKey(uuid));

        if (tracker.endBirdUUID != null && tracker.hasExactlyThreeRequiredEggs()) {
            // Records from versions before EncounterUUID are migrated only if they already
            // describe one unambiguous three-egg encounter. Polluted legacy data is discarded.
            tracker.encounterUUID = tag.hasUUID("EncounterUUID")
                    ? tag.getUUID("EncounterUUID") : UUID.randomUUID();
            tracker.completionPending = tag.getBoolean("CompletionPending")
                    || tracker.areAllEggsDestroyed();
        } else {
            tracker.eggTypes.clear();
            tracker.destroyedEggs.clear();
            tracker.endBirdUUID = null;
            tracker.completionKillerUUID = null;
            tracker.completionEggType = null;
            tracker.completionPending = false;
        }
        return tracker;
    }

    private static ListTag uuidList(Collection<String> values) {
        ListTag result = new ListTag();
        values.forEach(uuid -> result.add(StringTag.valueOf(uuid)));
        return result;
    }

    private static void readUUIDSet(CompoundTag tag, String key, Set<String> target, int limit) {
        ListTag values = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < values.size() && target.size() < limit; i++) {
            String value = values.getString(i);
            if (parseUUID(value) != null) target.add(value);
        }
    }

    private static void addBounded(Set<String> target, UUID uuid, int limit) {
        if (target.size() < limit) target.add(uuid.toString());
    }

    private static UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
