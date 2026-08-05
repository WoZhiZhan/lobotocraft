package com.wzz.lobotocraft.llm;

import com.wzz.lobotocraft.employee.EmployeeDepartment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LlmDepartmentAccessData extends SavedData {
    private static final String ID = "lobotocraft_llm_department_access";
    private final Map<UUID, Assignment> assignments = new LinkedHashMap<>();

    public static LlmDepartmentAccessData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                LlmDepartmentAccessData::load, LlmDepartmentAccessData::new, ID);
    }

    public static EnumSet<EmployeeDepartment> allDepartments() {
        EnumSet<EmployeeDepartment> result = EnumSet.allOf(EmployeeDepartment.class);
        result.remove(EmployeeDepartment.UNASSIGNED);
        return result;
    }

    public boolean isExplicit(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public Assignment assignment(UUID playerId) {
        return assignments.get(playerId);
    }

    public Collection<Assignment> assignments() {
        return List.copyOf(assignments.values());
    }

    public EnumSet<EmployeeDepartment> effectiveDepartments(ServerPlayer player) {
        Assignment assignment = assignments.get(player.getUUID());
        EnumSet<EmployeeDepartment> result = assignment != null ? assignment.departmentCopy()
                : player.hasPermissions(LlmConfigService.CONFIG_PERMISSION_LEVEL)
                ? allDepartments() : EnumSet.noneOf(EmployeeDepartment.class);
        result.removeIf(department -> !com.wzz.lobotocraft.employee.DepartmentProgressService
                .isOpen(player.server, department));
        return result;
    }

    public boolean canManage(ServerPlayer player, EmployeeDepartment department) {
        return department != EmployeeDepartment.UNASSIGNED
                && effectiveDepartments(player).contains(department);
    }

    public void setAssignment(UUID playerId, String playerName,
                              Collection<EmployeeDepartment> departments) {
        EnumSet<EmployeeDepartment> clean = EnumSet.noneOf(EmployeeDepartment.class);
        for (EmployeeDepartment department : departments) {
            if (department != EmployeeDepartment.UNASSIGNED) clean.add(department);
        }
        assignments.put(playerId, new Assignment(playerId,
                playerName == null || playerName.isBlank() ? playerId.toString() : playerName, clean));
        setDirty();
    }

    public static LlmDepartmentAccessData load(CompoundTag tag) {
        LlmDepartmentAccessData data = new LlmDepartmentAccessData();
        ListTag list = tag.getList("Assignments", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("PlayerId")) continue;
            UUID playerId = entry.getUUID("PlayerId");
            EnumSet<EmployeeDepartment> departments = EnumSet.noneOf(EmployeeDepartment.class);
            ListTag departmentTags = entry.getList("Departments", Tag.TAG_STRING);
            for (int departmentIndex = 0; departmentIndex < departmentTags.size(); departmentIndex++) {
                EmployeeDepartment department = EmployeeDepartment.byId(
                        departmentTags.getString(departmentIndex));
                if (department != EmployeeDepartment.UNASSIGNED) departments.add(department);
            }
            data.assignments.put(playerId, new Assignment(playerId,
                    entry.getString("PlayerName"), departments));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Assignment assignment : assignments.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("PlayerId", assignment.playerId());
            entry.putString("PlayerName", assignment.playerName());
            ListTag departments = new ListTag();
            assignment.departments().forEach(department ->
                    departments.add(StringTag.valueOf(department.id())));
            entry.put("Departments", departments);
            list.add(entry);
        }
        tag.put("Assignments", list);
        return tag;
    }

    public record Assignment(UUID playerId, String playerName,
                             Set<EmployeeDepartment> departments) {
        public Assignment {
            departments = Set.copyOf(departments);
        }

        public EnumSet<EmployeeDepartment> departmentCopy() {
            return departments.isEmpty() ? EnumSet.noneOf(EmployeeDepartment.class)
                    : EnumSet.copyOf(departments);
        }
    }
}
