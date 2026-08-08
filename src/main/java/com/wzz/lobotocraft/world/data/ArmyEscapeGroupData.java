package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent authority for Army in Black escape-group completion. */
public final class ArmyEscapeGroupData extends SavedData {
    private static final String NAME = "army_in_black_escape_groups";
    private final Map<UUID, Group> groups = new HashMap<>();

    public static ArmyEscapeGroupData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ArmyEscapeGroupData::load, ArmyEscapeGroupData::new, NAME);
    }

    public void create(UUID groupId, int expectedMembers) {
        groups.put(groupId, new Group(Math.max(1, expectedMembers)));
        setDirty();
    }

    public void register(UUID groupId, UUID memberId, int expectedMembers) {
        Group group = groups.get(groupId);
        boolean changed = false;
        if (group == null) {
            group = new Group(Math.max(1, expectedMembers));
            groups.put(groupId, group);
            changed = true;
        }
        changed |= group.members.add(memberId);
        if (group.closed) changed |= group.pendingCleanup.add(memberId);
        if (changed) setDirty();
    }

    public boolean markDetonated(UUID groupId, UUID memberId, int expectedMembers) {
        register(groupId, memberId, expectedMembers);
        Group group = groups.get(groupId);
        if (group.closed) return true;
        if (group.detonated.add(memberId)) setDirty();
        return group.detonated.size() >= group.expectedMembers;
    }

    public boolean removeUndetonatedMember(UUID groupId, UUID memberId) {
        Group group = groups.get(groupId);
        if (group == null || group.closed || group.detonated.contains(memberId)) return false;
        if (group.members.remove(memberId)) {
            group.expectedMembers = Math.max(group.detonated.size(), group.expectedMembers - 1);
            setDirty();
        }
        return group.expectedMembers <= 0 || group.detonated.size() >= group.expectedMembers;
    }

    public void removeUnspawnedMember(UUID groupId, UUID memberId) {
        removeUndetonatedMember(groupId, memberId);
    }

    public void recordSpawnFailure(UUID groupId) {
        Group group = groups.get(groupId);
        if (group == null || group.closed) return;
        group.expectedMembers = Math.max(group.detonated.size(), group.expectedMembers - 1);
        setDirty();
    }

    public void close(UUID groupId) {
        Group group = groups.computeIfAbsent(groupId, ignored -> new Group(1));
        boolean changed = !group.closed;
        group.closed = true;
        changed |= group.pendingCleanup.addAll(group.members);
        if (changed) setDirty();
    }

    public boolean isClosed(UUID groupId) {
        Group group = groups.get(groupId);
        return group != null && group.closed;
    }

    public void acknowledgeCleanup(UUID groupId, UUID memberId) {
        Group group = groups.get(groupId);
        if (group == null || !group.closed) return;
        group.pendingCleanup.remove(memberId);
        setDirty();
        // Legacy groups may first be discovered one chunk at a time. Keep the
        // tombstone until every member promised by entity NBT has checked in.
        if (group.pendingCleanup.isEmpty() && group.members.size() >= group.expectedMembers) {
            groups.remove(groupId);
        }
    }

    int groupCount() {
        return groups.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        groups.forEach((groupId, group) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Group", groupId);
            entry.putInt("Expected", group.expectedMembers);
            entry.putBoolean("Closed", group.closed);
            entry.put("Members", saveUuids(group.members));
            entry.put("Detonated", saveUuids(group.detonated));
            entry.put("PendingCleanup", saveUuids(group.pendingCleanup));
            list.add(entry);
        });
        tag.put("Groups", list);
        return tag;
    }

    public static ArmyEscapeGroupData load(CompoundTag tag) {
        ArmyEscapeGroupData data = new ArmyEscapeGroupData();
        ListTag list = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Group")) continue;
            Group group = new Group(Math.max(1, entry.getInt("Expected")));
            group.closed = entry.getBoolean("Closed");
            loadUuids(entry.getList("Members", Tag.TAG_COMPOUND), group.members);
            loadUuids(entry.getList("Detonated", Tag.TAG_COMPOUND), group.detonated);
            loadUuids(entry.getList("PendingCleanup", Tag.TAG_COMPOUND), group.pendingCleanup);
            if (group.closed && group.pendingCleanup.isEmpty()) group.pendingCleanup.addAll(group.members);
            data.groups.put(entry.getUUID("Group"), group);
        }
        return data;
    }

    private static ListTag saveUuids(Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            list.add(entry);
        }
        return list;
    }

    private static void loadUuids(ListTag list, Set<UUID> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) target.add(entry.getUUID("Id"));
        }
    }

    private static final class Group {
        private int expectedMembers;
        private boolean closed;
        private final Set<UUID> members = new HashSet<>();
        private final Set<UUID> detonated = new HashSet<>();
        private final Set<UUID> pendingCleanup = new HashSet<>();

        private Group(int expectedMembers) {
            this.expectedMembers = expectedMembers;
        }
    }
}
