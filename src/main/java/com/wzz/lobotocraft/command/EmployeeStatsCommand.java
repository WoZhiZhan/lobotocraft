package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.event.EmployeeStatsApplier;
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
                                        .then(Commands.argument("value", IntegerArgumentType.integer(20, 100))
                                                .executes(context -> setFortitude(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 谨慎
                                .then(Commands.literal("prudence")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(20, 100))
                                                .executes(context -> setPrudence(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 自律
                                .then(Commands.literal("temperance")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(20, 100))
                                                .executes(context -> setTemperance(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                // 正义
                                .then(Commands.literal("justice")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(20, 100))
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
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a设置 %s 的勇气值为 %d (等级 %d)",
                            player.getName().getString(), value, stats.getFortitudeLevel())), true);
        });
        return 1;
    }

    private int setPrudence(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setPrudence(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a设置 %s 的谨慎值为 %d (等级 %d)",
                            player.getName().getString(), value, stats.getPrudenceLevel())), true);
        });
        return 1;
    }

    private int setTemperance(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setTemperance(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a设置 %s 的自律值为 %d (等级 %d)",
                            player.getName().getString(), value, stats.getTemperanceLevel())), true);
        });
        return 1;
    }

    private int setJustice(CommandContext<CommandSourceStack> context, ServerPlayer player, int value) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setJustice(value);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a设置 %s 的正义值为 %d (等级 %d)",
                            player.getName().getString(), value, stats.getJusticeLevel())), true);
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
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a为 %s %s %d 勇气值，当前: %d (等级 %d)",
                            player.getName().getString(),
                            amount > 0 ? "增加" : "减少",
                            Math.abs(actualIncrease),
                            stats.getFortitude(),
                            stats.getFortitudeLevel())), true);
        });
        return 1;
    }

    private int addPrudence(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addPrudence(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a为 %s %s %d 谨慎值，当前: %d (等级 %d)",
                            player.getName().getString(),
                            amount > 0 ? "增加" : "减少",
                            Math.abs(actualIncrease),
                            stats.getPrudence(),
                            stats.getPrudenceLevel())), true);
        });
        return 1;
    }

    private int addTemperance(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addTemperance(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a为 %s %s %d 自律值，当前: %d (等级 %d)",
                            player.getName().getString(),
                            amount > 0 ? "增加" : "减少",
                            Math.abs(actualIncrease),
                            stats.getTemperance(),
                            stats.getTemperanceLevel())), true);
        });
        return 1;
    }

    private int addJustice(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            int actualIncrease = stats.addJustice(amount);
            syncToClient(player, stats);
            // 重新应用属性效果
            EmployeeStatsApplier.applyAllAttributes(player);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a为 %s %s %d 正义值，当前: %d (等级 %d)",
                            player.getName().getString(),
                            amount > 0 ? "增加" : "减少",
                            Math.abs(actualIncrease),
                            stats.getJustice(),
                            stats.getJusticeLevel())), true);
        });
        return 1;
    }

    // ==================== Get 方法 ====================

    private int getStats(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§e%s 的员工属性:\n" +
                                  "§c勇气 (Fortitude): %d (等级 %d)\n" +
                                  "§b谨慎 (Prudence): %d (等级 %d)\n" +
                                  "§a自律 (Temperance): %d (等级 %d)\n" +
                                  "§6正义 (Justice): %d (等级 %d)\n" +
                                  "§d员工总等级: %d",
                            player.getName().getString(),
                            stats.getFortitude(), stats.getFortitudeLevel(),
                            stats.getPrudence(), stats.getPrudenceLevel(),
                            stats.getTemperance(), stats.getTemperanceLevel(),
                            stats.getJustice(), stats.getJusticeLevel(),
                            stats.getEmployeeLevel())), false);
        });
        return 1;
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