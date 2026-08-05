package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.employee.EmployeeDepartment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client mirror used by PlayerEvent.NameFormat for in-world nameplates. */
public final class PlayerDepartmentClientState {
    private static final Map<UUID, EmployeeDepartment> DEPARTMENTS = new ConcurrentHashMap<>();

    private PlayerDepartmentClientState() {
    }

    public static void update(UUID playerId, String departmentId) {
        if (playerId == null) return;
        EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
        if (department == EmployeeDepartment.UNASSIGNED) DEPARTMENTS.remove(playerId);
        else DEPARTMENTS.put(playerId, department);
    }

    public static EmployeeDepartment department(UUID playerId) {
        return DEPARTMENTS.getOrDefault(playerId, EmployeeDepartment.UNASSIGNED);
    }

    public static void clear() {
        DEPARTMENTS.clear();
    }
}
