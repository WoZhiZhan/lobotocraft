package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.capability.CompanyDailyData;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 补齐当前核心抑制的全部结束条件，用于测试或管理。
 */
public class CompleteCoreSuppressionCommand implements ICommand {

    @Override
    public String getName() {
        return "completecoresuppression";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(this::completeRequirements);
    }

    private int completeRequirements(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        CoreSuppressionData suppression = CoreSuppressionData.get(server.overworld());
        if (!suppression.isActive()) {
            source.sendFailure(Component.literal("§c当前没有正在进行的核心抑制"));
            return 0;
        }

        ServerPlayer owner = server.getPlayerList().getPlayer(suppression.getOwnerUuid());
        if (owner == null) {
            source.sendFailure(Component.literal("§c核心抑制接取者不在线，无法补齐条件"));
            return 0;
        }

        CompanyDailyData daily = owner.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .resolve()
                .orElse(null);
        if (daily == null) {
            source.sendFailure(Component.literal("§c无法读取核心抑制接取者的公司日程数据"));
            return 0;
        }

        daily.setOwner(owner);
        daily.setTodayWorkCount(Math.max(daily.getTodayWorkCount(), daily.getRequiredWorkCount()));
        daily.setHasSleep(false);

        OrdealData ordeal = OrdealData.get(server.overworld());
        ordeal.setCompletionsToday(
                Math.max(ordeal.getDawnCompletionsToday(), CoreSuppressionManager.REQUIRED_DAWNS),
                Math.max(ordeal.getMiddayCompletionsToday(), CoreSuppressionManager.REQUIRED_MIDDAYS)
        );
        suppression.setProgress(ordeal.getDawnCompletionsToday(), ordeal.getMiddayCompletionsToday());

        MessageLoader.getLoader().sendToPlayer(owner, new CompanyDailySyncPacket(
                daily.getCurrentDay(),
                daily.getTodayWorkCount(),
                daily.isArmorLocked(),
                daily.isHasSleep()
        ));
        CoreSuppressionManager.syncAll(server);

        source.sendSuccess(() -> Component.literal(String.format(
                "§a已补齐 %s 核心抑制条件 §7(黎明 %d/%d，正午 %d/%d，工作 %d/%d)；接取者现在可以休息",
                suppression.getActiveType().getDisplayName(),
                suppression.getDawnCompleted(), CoreSuppressionManager.REQUIRED_DAWNS,
                suppression.getMiddayCompleted(), CoreSuppressionManager.REQUIRED_MIDDAYS,
                daily.getTodayWorkCount(), daily.getRequiredWorkCount()
        )), true);
        return 1;
    }
}
