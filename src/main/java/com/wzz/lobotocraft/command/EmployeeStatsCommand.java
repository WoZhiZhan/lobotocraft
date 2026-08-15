package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 员工属性设置指令
 * 用法：
 * /employeestats set <player> fortitude <value>    - 设置勇气值
 * /employeestats set <player> prudence <value>     - 设置谨慎值
 * /employeestats set <player> temperance <value>   - 设置自律值
 * /employeestats set <player> justice <value>      - 设置正义值
 * /employeestats get <player>                      - 查看所有属性
 */
public class EmployeeStatsCommand implements ICommand {

    @Override
    public String getName() {
        return "employeestats";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                // /employeestats set <player> fortitude <value>
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                // 勇气
                                .then(Commands.literal("fortitude")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                                        EmployeeStats.MIN_STAT, EmployeeStats.MAX_STAT))
                                                .executes(context -> setFortitude(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 谨慎
                                .then(Commands.literal("prudence")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                                        EmployeeStats.MIN_STAT, EmployeeStats.MAX_STAT))
                                                .executes(context -> setPrudence(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 自律
                                .then(Commands.literal("temperance")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                                        EmployeeStats.MIN_STAT, EmployeeStats.MAX_STAT))
                                                .executes(context -> setTemperance(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 正义
                                .then(Commands.literal("justice")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                                        EmployeeStats.MIN_STAT, EmployeeStats.MAX_STAT))
                                                .executes(context -> setJustice(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))))
                
                // /employeestats add <player> fortitude <amount>
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                // 勇气
                                .then(Commands.literal("fortitude")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> addFortitude(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))
                                // 谨慎
                                .then(Commands.literal("prudence")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> addPrudence(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))
                                // 自律
                                .then(Commands.literal("temperance")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> addTemperance(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))
                                // 正义
                                .then(Commands.literal("justice")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> addJustice(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                
                // /employeestats get <player>
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getStats(context,
                                        EntityArgument.getPlayer(context, "player")))));
    }

    // ==================== Set 方法 ====================

    private int setFortitude(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statSetMessage(player, "fortitude", value,
                    EmployeeLevelCalculator.statGrade(stats.getFortitude())), true);
        });
        return 1;
    }

    private int setPrudence(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setPrudence(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statSetMessage(player, "prudence", value,
                    EmployeeLevelCalculator.statGrade(stats.getPrudence())), true);
        });
        return 1;
    }

    private int setTemperance(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setTemperance(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statSetMessage(player, "temperance", value,
                    EmployeeLevelCalculator.statGrade(stats.getTemperance())), true);
        });
        return 1;
    }

    private int setJustice(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setJustice(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statSetMessage(player, "justice", value,
                    EmployeeLevelCalculator.statGrade(stats.getJustice())), true);
        });
        return 1;
    }

    // ==================== Add 方法 ====================

    private int addFortitude(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addFortitude(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statAddMessage(player, "fortitude", amount,
                    actualIncrease, stats.getFortitude(),
                    EmployeeLevelCalculator.statGrade(stats.getFortitude())), true);
        });
        return 1;
    }

    private int addPrudence(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addPrudence(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statAddMessage(player, "prudence", amount,
                    actualIncrease, stats.getPrudence(),
                    EmployeeLevelCalculator.statGrade(stats.getPrudence())), true);
        });
        return 1;
    }

    private int addTemperance(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addTemperance(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statAddMessage(player, "temperance", amount,
                    actualIncrease, stats.getTemperance(),
                    EmployeeLevelCalculator.statGrade(stats.getTemperance())), true);
        });
        return 1;
    }

    private int addJustice(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addJustice(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> statAddMessage(player, "justice", amount,
                    actualIncrease, stats.getJustice(),
                    EmployeeLevelCalculator.statGrade(stats.getJustice())), true);
        });
        return 1;
    }

    // ==================== Get 方法 ====================

    private int getStats(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.employee_stats.summary", player.getDisplayName(),
                    stats.getFortitude(), EmployeeLevelCalculator.statGrade(stats.getFortitude()),
                    stats.getPrudence(), EmployeeLevelCalculator.statGrade(stats.getPrudence()),
                    stats.getTemperance(), EmployeeLevelCalculator.statGrade(stats.getTemperance()),
                    stats.getJustice(), EmployeeLevelCalculator.statGrade(stats.getJustice()),
                    stats.getEmployeeLevel()), false);
        });
        return 1;
    }

    private Component statSetMessage(ServerPlayer player, String stat, int value, String grade) {
        return Component.translatable("command.lobotocraft.employee_stats.set", player.getDisplayName(),
                Component.translatable("stat.lobotocraft." + stat), value, grade);
    }

    private Component statAddMessage(ServerPlayer player, String stat, int requestedAmount,
                                     int actualAmount, int current, String grade) {
        return Component.translatable("command.lobotocraft.employee_stats.add", player.getDisplayName(),
                Component.translatable(requestedAmount > 0
                        ? "command.lobotocraft.action.increase"
                        : "command.lobotocraft.action.decrease"),
                Math.abs(actualAmount), Component.translatable("stat.lobotocraft." + stat),
                current, grade);
    }

    // ==================== 辅助方法 ====================

    /**
     * 同步员工属性到客户端
     */
    private void syncToClient(ServerPlayer player, com.wzz.lobotocraft.capability.IEmployeeStats stats) {
        MessageLoader.getLoader().sendToPlayer(player,
                new EmployeeStatsSyncPacket(
                        stats.getFortitude(),
                        stats.getPrudence(),
                        stats.getTemperance(),
                        stats.getJustice()
                ));
    }
}
