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
                .then(buildColorResistance("red"))
                .then(buildColorResistance("white"))
                .then(buildColorResistance("black"))
                .then(buildColorResistance("blue"))
                .then(Commands.literal("getall")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getAllResistances(context,
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildColorResistance(String color) {
        return Commands.literal(color)
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(-1, 10))
                                        .executes(context -> setResistance(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"), color)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(context -> addResistance(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "amount"), color)))))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getResistance(context,
                                        EntityArgument.getPlayer(context, "player"), color))));
    }

    private int setResistance(CommandContext<CommandSourceStack> context, ServerPlayer player,
                              float value, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            attribute.setBaseValue(value);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.resistance.set", player.getDisplayName(),
                    getTypeName(type), value), true);
        }
        return 1;
    }

    private int addResistance(CommandContext<CommandSourceStack> context, ServerPlayer player,
                              float amount, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            double currentValue = attribute.getBaseValue();
            double newValue = Math.max(-1.0D, Math.min(10.0D, currentValue + amount));
            attribute.setBaseValue(newValue);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.resistance.add", player.getDisplayName(),
                    getTypeName(type), Component.translatable(amount > 0
                            ? "command.lobotocraft.action.increase"
                            : "command.lobotocraft.action.decrease"),
                    Math.abs(amount), newValue), true);
        }
        return 1;
    }

    private int getResistance(CommandContext<CommandSourceStack> context, ServerPlayer player, String type) {
        AttributeInstance attribute = getAttributeByType(player, type);
        if (attribute != null) {
            double value = attribute.getBaseValue();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.resistance.get", player.getDisplayName(),
                    getTypeName(type), value, getResistanceStatus(value)), false);
        }
        return 1;
    }

    private int getAllResistances(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        double redRes = player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        double whiteRes = player.getAttributeValue(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        double blackRes = player.getAttributeValue(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        double paleRes = player.getAttributeValue(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());

        context.getSource().sendSuccess(() -> Component.translatable(
                "command.lobotocraft.resistance.get_all", player.getDisplayName(),
                redRes, getResistanceStatus(redRes),
                whiteRes, getResistanceStatus(whiteRes),
                blackRes, getResistanceStatus(blackRes),
                paleRes, getResistanceStatus(paleRes)), false);
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

    private Component getTypeName(String type) {
        String languageType = "blue".equals(type) ? "pale" : type;
        return Component.translatable("command.lobotocraft.damage_type." + languageType);
    }

    private Component getResistanceStatus(double value) {
        if (value <= -1.0D) {
            return Component.translatable("command.lobotocraft.resistance.status.absorb");
        } else if (value <= 0.0D) {
            return Component.translatable("command.lobotocraft.resistance.status.immune");
        } else if (value < 1.0D) {
            return Component.translatable("command.lobotocraft.resistance.status.resistant");
        } else if (value == 1.0D) {
            return Component.translatable("command.lobotocraft.resistance.status.normal");
        }
        return Component.translatable("command.lobotocraft.resistance.status.vulnerable");
    }
}
