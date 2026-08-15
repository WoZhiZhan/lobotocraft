package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.employee.DepartmentProgressService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class EmployeeCommand implements ICommand {
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("department", StringArgumentType.word())
                                        .executes(context -> create(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "department"))))))
                .then(Commands.literal("move")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", Vec3Argument.vec3())
                                        .executes(context -> move(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                Vec3Argument.getVec3(context, "target"))))))
                .then(Commands.literal("work")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(context -> work(context.getSource(),
                                                        StringArgumentType.getString(context, "employee"),
                                                        EntityArgument.getEntity(context, "target"),
                                                        StringArgumentType.getString(context, "type")))))))
                .then(Commands.literal("suppress")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> suppress(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .executes(context -> remove(context.getSource(),
                                        StringArgumentType.getString(context, "employee")))));
    }

    @Override
    public String getName() {
        return "employee";
    }

    private int create(CommandSourceStack source, String name, String departmentId) {
        ServerPlayer owner;
        try {
            owner = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.lobotocraft.player_only"));
            return 0;
        }
        EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
        if (department == EmployeeDepartment.UNASSIGNED
                && !EmployeeDepartment.UNASSIGNED.id().equals(departmentId)) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.employee.unknown_department", departmentId));
            return 0;
        }
        if (department != EmployeeDepartment.UNASSIGNED) {
            if (!DepartmentProgressService.isOpen(source.getServer(), department)) {
                source.sendFailure(Component.translatable(
                        "command.lobotocraft.employee.department_closed",
                        Component.translatable(department.translationKey())));
                return 0;
            }
            long assigned = FacilityPersonnelData.get(source.getServer()).employees().stream()
                    .filter(record -> record.department() == department)
                    .filter(record -> department != EmployeeDepartment.CENTRAL
                            || record.centralSection() == 0)
                    .count();
            int capacity = DepartmentProgressService.employeeCapacity(
                    source.getServer(), department, 0);
            if (assigned >= capacity) {
                Component departmentName = Component.translatable(department.translationKey());
                source.sendFailure(Component.translatable(
                        department == EmployeeDepartment.CENTRAL
                                ? "command.lobotocraft.employee.department_full.central_upper"
                                : "command.lobotocraft.employee.department_full",
                        departmentName, capacity));
                return 0;
            }
        }
        var result = com.wzz.lobotocraft.employee.RecruitmentService.recruit(owner, name,
                1, 1, 1, 1);
        if (!result.success()) {
            source.sendFailure(result.message());
            return 0;
        }
        EmployeeRecord record = result.employee();
        record.setDepartment(department);
        FacilityPersonnelData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.employee.deployed",
                record.name(), Component.translatable(record.department().translationKey())), true);
        return 1;
    }

    private int move(CommandSourceStack source, String name, net.minecraft.world.phys.Vec3 target) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.assignMove(record, net.minecraft.core.BlockPos.containing(target))) {
            source.sendFailure(Component.translatable("command.lobotocraft.employee.not_deployed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.employee.move_assigned"), false);
        return 1;
    }

    private int work(CommandSourceStack source, String name, Entity target, String typeId) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!(target instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.employee.work_target_invalid"));
            return 0;
        }
        WorkType type = parseWorkType(typeId);
        if (type == null || !EmployeeRuntimeManager.assignWork(record, abnormality, type)) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.employee.work_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.employee.work_assigned",
                Component.translatable(type.getTranslationKey())), false);
        return 1;
    }

    private int suppress(CommandSourceStack source, String name, Entity target) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!(target instanceof LivingEntity living)
                || !EmployeeRuntimeManager.assignSuppress(record, living)) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.employee.suppress_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.employee.suppress_assigned"), false);
        return 1;
    }

    private int remove(CommandSourceStack source, String name) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        EmployeeRuntimeManager.remove(source.getServer(), record);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.employee.removed", name), true);
        return 1;
    }

    private EmployeeRecord find(CommandSourceStack source, String name) {
        EmployeeRecord record = FacilityPersonnelData.get(source.getServer()).findByName(name);
        if (record == null) source.sendFailure(Component.translatable(
                "command.lobotocraft.employee.not_found", name));
        return record;
    }

    private static WorkType parseWorkType(String id) {
        for (WorkType type : WorkType.values()) {
            if (type.getName().equalsIgnoreCase(id) || type.getDisplayName().equals(id)) return type;
        }
        return null;
    }
}
