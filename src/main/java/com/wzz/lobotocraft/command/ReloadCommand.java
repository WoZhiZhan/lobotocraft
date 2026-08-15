package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.event.listener.ClerkEvent;
import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class ReloadCommand implements ICommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(this::reload);
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.clerk_reset.dimension_missing"));
            return 0;
        }

        ClerkEvent.ClerkResetResult result = ClerkEvent.resetDayClerks(level);
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.clerk_reset.completed",
                result.clearedTombstones(), result.resetClerks(),
                result.spawnedClerks(), result.removedClerks()), true);
        return 1;
    }
}
