package com.wzz.lobotocraft.command;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.data.FacilityDayData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Fills the server-authoritative completion requirements for the active suppression. */
public class CompleteCoreSuppressionCommand implements ICommand {
    @Override
    public String getName() {
        return "completecoresuppression";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName()).executes(this::completeRequirements);
    }

    private int completeRequirements(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        CoreSuppressionData suppression = CoreSuppressionData.get(server.overworld());
        CoreSuppressionType type = suppression.getActiveType();
        if (!suppression.isActive() || type == null) {
            source.sendFailure(Component.literal("当前没有正在进行的核心抑制。"));
            return 0;
        }
        if (!type.isImplemented() || type.getRequiredBossPhases() > 0) {
            source.sendFailure(Component.literal(type.getDisplayName() + " 核心抑制尚未实装。"));
            return 0;
        }

        FacilityDayData facility = FacilityDayData.get(server.overworld());
        if (type.isEnergyQuotaRequired()) {
            facility.debugSetEnergy(facility.getEnergyQuota());
        }
        while (facility.getMeltdownLevel() < type.getRequiredMeltdownLevel()) {
            facility.finishMeltdownStage();
        }

        FacilityManager.syncAll(server, facility);
        CoreSuppressionManager.syncAll(server);
        source.sendSuccess(() -> Component.literal(type.getDisplayName()
                + " 核心抑制条件已补齐（能源 " + facility.getEnergy() + "/"
                + facility.getEnergyQuota() + "，熔毁等级 " + facility.getMeltdownLevel()
                + "/" + type.getRequiredMeltdownLevel() + "）。"), true);
        return 1;
    }
}
