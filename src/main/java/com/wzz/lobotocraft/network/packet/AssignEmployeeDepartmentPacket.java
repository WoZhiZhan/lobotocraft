package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.employee.DepartmentProgressService;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public final class AssignEmployeeDepartmentPacket implements IMessage {
    private UUID employeeId;
    private String departmentId;
    private int centralSection;
    private boolean reopenDeployment;

    public AssignEmployeeDepartmentPacket() {
    }

    public AssignEmployeeDepartmentPacket(UUID employeeId, String departmentId) {
        this(employeeId, departmentId, 0, false);
    }

    public AssignEmployeeDepartmentPacket(UUID employeeId, String departmentId,
                                          boolean reopenDeployment) {
        this(employeeId, departmentId, 0, reopenDeployment);
    }

    public AssignEmployeeDepartmentPacket(UUID employeeId, String departmentId,
                                          int centralSection, boolean reopenDeployment) {
        this.employeeId = employeeId;
        this.departmentId = departmentId;
        this.centralSection = centralSection;
        this.reopenDeployment = reopenDeployment;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        employeeId = buffer.readUUID();
        departmentId = buffer.readUtf(32);
        centralSection = buffer.readByte();
        reopenDeployment = buffer.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(employeeId);
        buffer.writeUtf(departmentId, 32);
        buffer.writeByte(centralSection);
        buffer.writeBoolean(reopenDeployment);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || !sender.hasPermissions(2)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        EmployeeRecord record = FacilityPersonnelData.get(sender.server).get(employeeId);
        EmployeeDepartment department = validDepartment(departmentId);
        if (record == null || department == null) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_department.invalid"));
            return;
        }
        FacilityPersonnelData personnel = FacilityPersonnelData.get(sender.server);
        int targetSection = department == EmployeeDepartment.CENTRAL
                ? Math.max(0, Math.min(1, centralSection)) : 0;
        if (!DepartmentProgressService.isSectionOpen(sender.server, department, targetSection)) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_department.not_open"));
            if (reopenDeployment) FacilityManagementService.openDepartmentAssignment(sender, employeeId);
            return;
        }
        long assigned = personnel.employees().stream()
                .filter(employee -> employee.department() == department)
                .filter(employee -> department != EmployeeDepartment.CENTRAL
                        || employee.centralSection() == targetSection)
                .count();
        boolean moving = record.department() != department
                || (department == EmployeeDepartment.CENTRAL
                && record.centralSection() != targetSection);
        int capacity = DepartmentProgressService.employeeCapacity(
                sender.server, department, targetSection);
        if (moving && assigned >= capacity) {
            Component departmentName = Component.translatable(department == EmployeeDepartment.CENTRAL
                    ? targetSection == 0 ? "department.lobotocraft.central.upper"
                    : "department.lobotocraft.central.lower"
                    : department.translationKey());
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_department.full", departmentName, capacity));
            if (reopenDeployment) {
                FacilityManagementService.openDepartmentAssignment(sender, employeeId);
            }
            return;
        }
        record.setDepartment(department);
        record.setCentralSection(targetSection);
        EmployeeRuntimeManager.refreshDepartmentAppearance(record);
        personnel.changed();
        if (reopenDeployment) {
            FacilityManagementService.openDepartmentAssignment(sender, employeeId);
        } else {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_department.assigned", record.name(),
                    Component.translatable(department.translationKey())));
            FacilityManagementService.open(sender);
        }
    }

    private static EmployeeDepartment validDepartment(String id) {
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department.id().equals(id)) return department;
        }
        return null;
    }
}
