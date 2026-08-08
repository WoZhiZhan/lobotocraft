package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-global player facility identities. These records never consume AI employee slots. */
public final class PlayerFacilityProfileData extends SavedData {
    private static final String ID = "lobotocraft_player_facility_profiles";

    private final Map<UUID, EmployeeDepartment> departments = new HashMap<>();

    public static PlayerFacilityProfileData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PlayerFacilityProfileData::load, PlayerFacilityProfileData::new, ID);
    }

    public synchronized EmployeeDepartment department(UUID playerId) {
        return departments.getOrDefault(playerId, EmployeeDepartment.UNASSIGNED);
    }

    public synchronized boolean setDepartment(UUID playerId, EmployeeDepartment department) {
        if (playerId == null || department == null) return false;
        EmployeeDepartment previous = department(playerId);
        if (department == EmployeeDepartment.UNASSIGNED) departments.remove(playerId);
        else departments.put(playerId, department);
        if (previous != department) setDirty();
        return true;
    }

    public static boolean canSelect(EmployeeDepartment department, boolean departmentOpen) {
        return department != null
                && department != EmployeeDepartment.UNASSIGNED
                && departmentOpen;
    }

    public static PlayerFacilityProfileData load(CompoundTag tag) {
        PlayerFacilityProfileData data = new PlayerFacilityProfileData();
        ListTag profiles = tag.getList("Profiles", Tag.TAG_COMPOUND);
        for (int index = 0; index < profiles.size(); index++) {
            CompoundTag profile = profiles.getCompound(index);
            if (!profile.hasUUID("Player")) continue;
            EmployeeDepartment department = EmployeeDepartment.byId(profile.getString("Department"));
            if (department != EmployeeDepartment.UNASSIGNED) {
                data.departments.put(profile.getUUID("Player"), department);
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag profiles = new ListTag();
        departments.forEach((playerId, department) -> {
            CompoundTag profile = new CompoundTag();
            profile.putUUID("Player", playerId);
            profile.putString("Department", department.id());
            profiles.add(profile);
        });
        tag.put("Profiles", profiles);
        return tag;
    }
}
