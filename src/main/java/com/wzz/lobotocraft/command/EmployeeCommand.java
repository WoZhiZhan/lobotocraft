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
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
        EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
        if (department == EmployeeDepartment.UNASSIGNED
                && !EmployeeDepartment.UNASSIGNED.id().equals(departmentId)) {
            source.sendFailure(Component.literal("未知部门：" + departmentId));
            return 0;
        }
        if (department != EmployeeDepartment.UNASSIGNED) {
            if (!DepartmentProgressService.isOpen(source.getServer(), department)) {
                source.sendFailure(Component.literal("该部门尚未开放：" + department.displayName()));
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
                source.sendFailure(Component.literal(department.displayName()
                        + (department == EmployeeDepartment.CENTRAL ? "上部" : "")
                        + "人数已满（" + capacity + "人）。"));
                return 0;
            }
        }
        var result = com.wzz.lobotocraft.employee.RecruitmentService.recruit(owner, name,
                1, 1, 1, 1);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        EmployeeRecord record = result.employee();
        record.setDepartment(department);
        FacilityPersonnelData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.literal("已部署AI员工 " + record.name()
                + " / " + record.department().displayName()), true);
        return 1;
    }

    private int move(CommandSourceStack source, String name, net.minecraft.world.phys.Vec3 target) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.assignMove(record, net.minecraft.core.BlockPos.containing(target))) {
            source.sendFailure(Component.literal("员工当前未部署。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已下达移动命令。"), false);
        return 1;
    }

    private int work(CommandSourceStack source, String name, Entity target, String typeId) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!(target instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("工作目标必须是异想体。"));
            return 0;
        }
        WorkType type = parseWorkType(typeId);
        if (type == null || !EmployeeRuntimeManager.assignWork(record, abnormality, type)) {
            source.sendFailure(Component.literal("无法下达工作命令。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已下达" + type.getDisplayName() + "工作命令。"), false);
        return 1;
    }

    private int suppress(CommandSourceStack source, String name, Entity target) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        if (!(target instanceof LivingEntity living)
                || !EmployeeRuntimeManager.assignSuppress(record, living)) {
            source.sendFailure(Component.literal("无法下达镇压命令。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已下达镇压命令。"), false);
        return 1;
    }

    private int remove(CommandSourceStack source, String name) {
        EmployeeRecord record = find(source, name);
        if (record == null) return 0;
        EmployeeRuntimeManager.remove(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("已移除AI员工 " + name), true);
        return 1;
    }

    private EmployeeRecord find(CommandSourceStack source, String name) {
        EmployeeRecord record = FacilityPersonnelData.get(source.getServer()).findByName(name);
        if (record == null) source.sendFailure(Component.literal("找不到AI员工：" + name));
        return record;
    }

    private static WorkType parseWorkType(String id) {
        for (WorkType type : WorkType.values()) {
            if (type.getName().equalsIgnoreCase(id) || type.getDisplayName().equals(id)) return type;
        }
        return null;
    }
}
