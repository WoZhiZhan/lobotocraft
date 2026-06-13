package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.init.ModAttributes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

public class ResistanceCommand implements ICommand {

    @Override
    public String getName() {
        return "resistance";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                // 红色伤害抗性
                .then(buildColorResistance("red"))
                // 白色伤害抗性
                .then(buildColorResistance("white"))
                // 黑色伤害抗性
                .then(buildColorResistance("black"))
                // 蓝色伤害抗性
                .then(buildColorResistance("blue"))
                // 获取所有抗性
                .then(Commands.literal("getall")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getAllResistances(context,
                                        EntityArgument.getPlayer(context, "player")))));
    }

    /**
     * 构建单个颜色的抗性指令分支
     */
    private LiteralArgumentBuilder<CommandSourceStack> buildColorResistance(String color) {
        return Commands.literal(color)
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(-1, 10))
                                        .executes(context -> setResistance(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"),
                                                color)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(context -> addResistance(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "amount"),
                                                color)))))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getResistance(context,
                                        EntityArgument.getPlayer(context, "player"),
                                        color))));
    }

    private int setResistance(CommandContext<CommandSourceStack> context, ServerPlayer player, float value, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            attribute.setBaseValue(value);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("设置 %s 的%s伤害抗性为 %.1f",
                            player.getName().getString(),
                            getTypeName(type),
                            value)), true);
        }
        return 1;
    }

    private int addResistance(CommandContext<CommandSourceStack> context, ServerPlayer player, float amount, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            double currentValue = attribute.getBaseValue();
            double newValue = Math.max(-1.0D, Math.min(10.0D, currentValue + amount));
            attribute.setBaseValue(newValue);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("为 %s 的%s伤害抗性 %s %.1f，当前: %.1f",
                            player.getName().getString(),
                            getTypeName(type),
                            amount > 0 ? "增加" : "减少",
                            Math.abs(amount),
                            newValue)), true);
        }
        return 1;
    }

    private int getResistance(CommandContext<CommandSourceStack> context, ServerPlayer player, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            double value = attribute.getBaseValue();
            String status = getResistanceStatus(value);
            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("%s 的%s伤害抗性: %.1f (%s)",
                            player.getName().getString(),
                            getTypeName(type),
                            value,
                            status)), false);
        }
        return 1;
    }

    private int getAllResistances(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        double redRes = player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        double whiteRes = player.getAttributeValue(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        double blackRes = player.getAttributeValue(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        double blueRes = player.getAttributeValue(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());

        context.getSource().sendSuccess(() -> Component.literal(
                String.format("%s 的伤害抗性:\n红色: %.1f (%s)\n白色: %.1f (%s)\n黑色: %.1f (%s)\n蓝色: %.1f (%s)",
                        player.getName().getString(),
                        redRes, getResistanceStatus(redRes),
                        whiteRes, getResistanceStatus(whiteRes),
                        blackRes, getResistanceStatus(blackRes),
                        blueRes, getResistanceStatus(blueRes))), false);
        return 1;
    }

    private AttributeInstance getAttributeByType(ServerPlayer player, String type) {
        return switch (type) {
            case "red" -> player.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
            case "white" -> player.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
            case "black" -> player.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
            case "blue" -> player.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
            default -> null;
        };
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "red" -> "红色";
            case "white" -> "白色";
            case "black" -> "黑色";
            case "blue" -> "蓝色";
            default -> "未知";
        };
    }

    private String getResistanceStatus(double value) {
        if (value <= -1.0D) {
            return "免疫并吸收";
        } else if (value <= 0.0D) {
            return "完全免疫";
        } else if (value < 1.0D) {
            return "有抗性";
        } else if (value == 1.0D) {
            return "正常伤害";
        } else {
            return "易伤";
        }
    }
}