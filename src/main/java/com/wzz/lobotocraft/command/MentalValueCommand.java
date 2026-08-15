package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MentalValueCommand implements ICommand {

    @Override
    public String getName() {
        return "mentalvalue";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                        .executes(context -> setMentalValue(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(context -> addMentalValue(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "amount"))))))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getMentalValue(context,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("setmax")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                        .executes(context -> setMaxMentalValue(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"))))));
    }

    private int setMentalValue(CommandContext<CommandSourceStack> context, ServerPlayer player, float value) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setMentalValue(value);
            syncToClient(player, mental.getMentalValue(), mental.getMaxMentalValue());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.mental_value.set", player.getDisplayName(), value), true);
        });
        return 1;
    }

    private int addMentalValue(CommandContext<CommandSourceStack> context, ServerPlayer player, float amount) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.addMentalValue(amount);
            syncToClient(player, mental.getMentalValue(), mental.getMaxMentalValue());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.mental_value.add", player.getDisplayName(),
                    Component.translatable(amount > 0
                            ? "command.lobotocraft.action.increase"
                            : "command.lobotocraft.action.decrease"),
                    Math.abs(amount), mental.getMentalValue(), mental.getEffectiveMaxMentalValue()), true);
        });
        return 1;
    }

    private int getMentalValue(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental ->
                context.getSource().sendSuccess(() -> Component.translatable(
                        "command.lobotocraft.mental_value.get", player.getDisplayName(),
                        mental.getMentalValue(), mental.getEffectiveMaxMentalValue()), false));
        return 1;
    }

    private int setMaxMentalValue(CommandContext<CommandSourceStack> context, ServerPlayer player, float value) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setMaxMentalValue(value);
            syncToClient(player, mental.getMentalValue(), mental.getMaxMentalValue());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.lobotocraft.mental_value.set_max", player.getDisplayName(), value), true);
        });
        return 1;
    }

    private void syncToClient(ServerPlayer player, float value, float maxValue) {
        MessageLoader.getLoader().sendToPlayer(player, new MentalValueSyncPacket(value, maxValue));
    }
}
