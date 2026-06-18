package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 直接补齐当天工作次数，用于测试或管理。
 */
public class CompleteDailyWorkCommand implements ICommand {

    @Override
    public String getName() {
        return "completedailywork";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(this::completeDailyWork);
    }

    private int completeDailyWork(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("§c该指令只能由玩家执行"));
            return 0;
        }

        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            data.setOwner(player);

            int required = data.getRequiredWorkCount();
            int current = data.getTodayWorkCount();

            if (current < required) {
                data.setTodayWorkCount(required);
                data.setHasSleep(false);
            }

            MessageLoader.getLoader().sendToPlayer(player,
                    new CompanyDailySyncPacket(
                            data.getCurrentDay(),
                            data.getTodayWorkCount(),
                            data.isArmorLocked(),
                            data.isHasSleep()
                    ));

            context.getSource().sendSuccess(() -> Component.literal(
                    String.format("§a今日工作已完成 §7(%d/%d)", data.getTodayWorkCount(), required)
            ), true);
        });

        return 1;
    }
}
