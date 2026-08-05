package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FacilityPersonnelData extends SavedData {
    private static final String ID = "lobotocraft_facility_personnel";
    private final Map<UUID, EmployeeRecord> employees = new LinkedHashMap<>();

    public static FacilityPersonnelData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FacilityPersonnelData::load, FacilityPersonnelData::new, ID);
    }

    public EmployeeRecord create(String name, EmployeeDepartment department) {
        if (findByName(name) != null) return null;
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name, department);
        employees.put(record.id(), record);
        setDirty();
        return record;
    }

    public EmployeeRecord get(UUID id) {
        return employees.get(id);
    }

    /** 活跃员工名册：排除已退休（死亡）的墓碑记录，供 GUI / 调度 / 招募查重使用。 */
    public Collection<EmployeeRecord> employees() {
        return employees.values().stream()
                .filter(record -> !record.retired())
                .toList();
    }

    /** 全量记录（含退休墓碑），仅供运行时管理器在启动初始化时清理墓碑用。 */
    public Collection<EmployeeRecord> allRecords() {
        return java.util.List.copyOf(employees.values());
    }

    public EmployeeRecord findByName(String name) {
        for (EmployeeRecord record : employees.values()) {
            if (record.retired()) continue;
            if (record.name().equalsIgnoreCase(name)) return record;
        }
        return null;
    }

    public boolean remove(UUID id) {
        if (employees.remove(id) == null) return false;
        setDirty();
        return true;
    }

    /**
     * 将员工标记为已退休（死亡墓碑）。不从磁盘删除记录，以便在崩溃/强杀后
     * 重启时仍能识别该员工已死、不再重新 spawn。调用方负责随后强制落盘。
     */
    public boolean retire(UUID id) {
        EmployeeRecord record = employees.get(id);
        if (record == null) return false;
        record.setRetired(true);
        record.setDeployed(false);
        setDirty();
        return true;
    }

    public void changed() {
        setDirty();
    }

    public static FacilityPersonnelData load(CompoundTag tag) {
        FacilityPersonnelData data = new FacilityPersonnelData();
        ListTag list = tag.getList("Employees", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            EmployeeRecord record = EmployeeRecord.load(list.getCompound(i));
            data.employees.put(record.id(), record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        employees.values().forEach(record -> list.add(record.save()));
        tag.put("Employees", list);
        return tag;
    }
}
