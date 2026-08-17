package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GeburahEncounterData extends SavedData {
    private static final String NAME = "lobotocraft_geburah_encounter";

    private boolean active;
    private UUID entityUuid;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> observers = new HashSet<>();
    private int startingPlayerCount;
    private int sharedLives;

    public static GeburahEncounterData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                GeburahEncounterData::load, GeburahEncounterData::new, NAME);
    }

    public void start(UUID entityUuid, Set<UUID> participants) {
        this.active = true;
        this.entityUuid = entityUuid;
        this.participants.clear();
        this.eliminated.clear();
        this.observers.clear();
        if (participants != null) this.participants.addAll(participants);
        this.startingPlayerCount = this.participants.size();
        this.sharedLives = startingPlayerCount <= 5 ? 5 : 0;
        setDirty();
    }

    public void clear() {
        if (!active && entityUuid == null && participants.isEmpty() && eliminated.isEmpty()
                && observers.isEmpty()
                && startingPlayerCount == 0 && sharedLives == 0) return;
        active = false;
        entityUuid = null;
        participants.clear();
        eliminated.clear();
        observers.clear();
        startingPlayerCount = 0;
        sharedLives = 0;
        setDirty();
    }

    public boolean isActive() {
        return active;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public boolean isParticipant(UUID playerUuid) {
        return playerUuid != null && participants.contains(playerUuid);
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public boolean isEliminated(UUID playerUuid) {
        return playerUuid != null && eliminated.contains(playerUuid);
    }

    public boolean isObserver(UUID playerUuid) {
        return playerUuid != null && observers.contains(playerUuid);
    }

    public Set<UUID> observers() {
        return Collections.unmodifiableSet(observers);
    }

    public void addObserver(UUID playerUuid) {
        if (playerUuid != null && !participants.contains(playerUuid) && observers.add(playerUuid)) setDirty();
    }

    public DeathOutcome consumeDeath(UUID playerUuid) {
        if (!isParticipant(playerUuid) || eliminated.contains(playerUuid)) return DeathOutcome.IGNORE;
        if (startingPlayerCount <= 5 && sharedLives > 0) {
            sharedLives--;
            setDirty();
            if (sharedLives > 0) return DeathOutcome.REVIVE;
        }
        eliminated.add(playerUuid);
        setDirty();
        return DeathOutcome.SPECTATE;
    }

    public int getStartingPlayerCount() {
        return startingPlayerCount;
    }

    public int getSharedLives() {
        return sharedLives;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        if (entityUuid != null) tag.putUUID("Entity", entityUuid);
        tag.putInt("StartingPlayerCount", startingPlayerCount);
        tag.putInt("SharedLives", sharedLives);
        ListTag players = new ListTag();
        for (UUID participant : participants) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", participant);
            players.add(entry);
        }
        tag.put("Participants", players);
        tag.put("Eliminated", saveUuidSet(eliminated));
        tag.put("Observers", saveUuidSet(observers));
        return tag;
    }

    public static GeburahEncounterData load(CompoundTag tag) {
        GeburahEncounterData data = new GeburahEncounterData();
        data.active = tag.getBoolean("Active");
        if (tag.hasUUID("Entity")) data.entityUuid = tag.getUUID("Entity");
        data.startingPlayerCount = Math.max(0, tag.getInt("StartingPlayerCount"));
        data.sharedLives = Math.max(0, tag.getInt("SharedLives"));
        ListTag players = tag.getList("Participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("Player")) data.participants.add(entry.getUUID("Player"));
        }
        loadUuidSet(tag.getList("Eliminated", Tag.TAG_COMPOUND), data.eliminated);
        loadUuidSet(tag.getList("Observers", Tag.TAG_COMPOUND), data.observers);
        return data;
    }

    private static ListTag saveUuidSet(Set<UUID> values) {
        ListTag result = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", value);
            result.add(entry);
        }
        return result;
    }

    private static void loadUuidSet(ListTag values, Set<UUID> target) {
        for (int i = 0; i < values.size(); i++) {
            CompoundTag entry = values.getCompound(i);
            if (entry.hasUUID("Player")) target.add(entry.getUUID("Player"));
        }
    }

    public enum DeathOutcome {
        IGNORE,
        REVIVE,
        SPECTATE
    }
}
