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
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.core_suppression.none_active"));
            return 0;
        }
        if (!type.isImplemented() || type.getRequiredBossPhases() > 0) {
            source.sendFailure(Component.translatable(
                    "command.lobotocraft.core_suppression.unimplemented",
                    Component.translatable(type.getTranslationKey())));
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
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.core_suppression.completed_conditions",
                Component.translatable(type.getTranslationKey()), facility.getEnergy(),
                facility.getEnergyQuota(), facility.getMeltdownLevel(),
                type.getRequiredMeltdownLevel()), true);
        return 1;
    }
}
