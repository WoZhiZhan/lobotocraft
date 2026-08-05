package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.llm.LlmConfigService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class LlmConfigCommand implements ICommand {
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(LlmConfigService.CONFIG_PERMISSION_LEVEL)
                        && source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    LlmConfigService.open(context.getSource().getPlayerOrException());
                    return 1;
                });
    }

    @Override
    public String getName() {
        return "llmconfig";
    }
}
