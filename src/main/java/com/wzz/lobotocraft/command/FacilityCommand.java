package com.wzz.lobotocraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.employee.DepartmentProgressService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class FacilityCommand {
    private FacilityCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("facility")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    FacilityManagementService.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("expansion")
                        .executes(context -> {
                            DepartmentProgressService.open(
                                    context.getSource().getPlayerOrException(), true);
                            return 1;
                        })
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("department", StringArgumentType.word())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                                                .executes(context -> setLevel(context.getSource(),
                                                        StringArgumentType.getString(context, "department"),
                                                        IntegerArgumentType.getInteger(context, "level"))))))));
    }

    private static int setLevel(CommandSourceStack source, String id, int level) {
        EmployeeDepartment department = EmployeeDepartment.byId(id);
        if (department == EmployeeDepartment.UNASSIGNED) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("未知部门：" + id));
            return 0;
        }
        if (!DepartmentProgressData.get(source.getServer()).setLevel(department, level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "等级无效；控制部不能设为 0。"));
            return 0;
        }
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "已将 " + department.displayName() + " 设置为 LEVEL " + level), true);
        return 1;
    }
}
