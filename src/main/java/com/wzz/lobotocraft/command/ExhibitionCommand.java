package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.exhibition.ExhibitionSafetyService;
import com.wzz.lobotocraft.world.data.ExhibitionModeData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class ExhibitionCommand implements ICommand {
    private static final String ROOT = "/exhibition ";

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(context -> sendPanel(context.getSource()))
                .then(Commands.literal("status")
                        .executes(context -> sendPanel(context.getSource())))
                .then(Commands.literal("start")
                        .executes(context -> start(context.getSource())))
                .then(Commands.literal("recover")
                        .executes(context -> recover(context.getSource())))
                .then(Commands.literal("stop-work")
                        .executes(context -> stopWork(context.getSource())))
                .then(Commands.literal("clear-status")
                        .executes(context -> clearStatus(context.getSource())))
                .then(Commands.literal("return-abnormalities")
                        .executes(context -> returnAbnormalities(context.getSource())))
                .then(Commands.literal("reset-ai")
                        .executes(context -> resetAi(context.getSource())))
                .then(Commands.literal("cleanup-events")
                        .executes(context -> cleanupEvents(context.getSource())))
                .then(Commands.literal("gauge")
                        .executes(context -> advanceGauge(context.getSource())))
                .then(Commands.literal("max-ai")
                        .executes(context -> maxAi(context.getSource())))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource())));
    }

    @Override
    public String getName() {
        return "exhibition";
    }

    public static int sendPanel(CommandSourceStack source) {
        boolean enabled = ExhibitionSafetyService.isEnabled(source.getServer());
        long recoveries = ExhibitionModeData.get(source.getServer()).getRecoverySerial();
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.exhibition.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.exhibition.status")
                .append(Component.translatable(enabled
                                ? "command.lobotocraft.exhibition.protection.enabled"
                                : "command.lobotocraft.exhibition.protection.disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.translatable("command.lobotocraft.exhibition.recoveries", recoveries)
                        .withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> button("command.lobotocraft.exhibition.button.start",
                        ROOT + "start", ChatFormatting.GREEN,
                        "command.lobotocraft.exhibition.hover.start")
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.recover",
                        ROOT + "recover", ChatFormatting.YELLOW,
                        "command.lobotocraft.exhibition.hover.recover"))
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.stop",
                        ROOT + "stop", ChatFormatting.RED,
                        "command.lobotocraft.exhibition.hover.stop")), false);
        source.sendSuccess(() -> button("command.lobotocraft.exhibition.button.stop_work",
                        ROOT + "stop-work", ChatFormatting.YELLOW,
                        "command.lobotocraft.exhibition.hover.stop_work")
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.clear_status",
                        ROOT + "clear-status", ChatFormatting.AQUA,
                        "command.lobotocraft.exhibition.hover.clear_status"))
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.return_abnormalities",
                        ROOT + "return-abnormalities", ChatFormatting.GREEN,
                        "command.lobotocraft.exhibition.hover.return_abnormalities")), false);
        source.sendSuccess(() -> button("command.lobotocraft.exhibition.button.reset_ai",
                        ROOT + "reset-ai", ChatFormatting.AQUA,
                        "command.lobotocraft.exhibition.hover.reset_ai")
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.cleanup_events",
                        ROOT + "cleanup-events", ChatFormatting.RED,
                        "command.lobotocraft.exhibition.hover.cleanup_events"))
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.gauge",
                        ROOT + "gauge", ChatFormatting.LIGHT_PURPLE,
                        "command.lobotocraft.exhibition.hover.gauge"))
                .append(Component.literal("  "))
                .append(button("command.lobotocraft.exhibition.button.max_ai",
                        ROOT + "max-ai", ChatFormatting.GOLD,
                        "command.lobotocraft.exhibition.hover.max_ai")), false);
        source.sendSuccess(() -> Component.translatable("command.lobotocraft.exhibition.note")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        ExhibitionSafetyService.setEnabled(source.getServer(), true);
        ExhibitionSafetyService.RecoveryReport report = ExhibitionSafetyService.recover(source.getServer());
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("command.lobotocraft.exhibition.started")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        sendRecoveryReport(source, report);
        return sendPanel(source);
    }

    private static int recover(CommandSourceStack source) {
        ExhibitionSafetyService.RecoveryReport report = ExhibitionSafetyService.recover(source.getServer());
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("command.lobotocraft.exhibition.recovered")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        sendRecoveryReport(source, report);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ExhibitionSafetyService.setEnabled(source.getServer(), false);
        source.getServer().saveEverything(false, true, true);
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("command.lobotocraft.exhibition.stopped")
                        .withStyle(ChatFormatting.YELLOW), false);
        return sendPanel(source);
    }

    private static int stopWork(CommandSourceStack source) {
        ExhibitionSafetyService.WorkStopReport report =
                ExhibitionSafetyService.stopAllWork(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.stop_work.result", report.works(), report.orders())
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int clearStatus(CommandSourceStack source) {
        int players = ExhibitionSafetyService.clearAllControlStates(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.clear_status.result", players)
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int returnAbnormalities(CommandSourceStack source) {
        int abnormalities = ExhibitionSafetyService.returnAllAbnormalities(source.getServer());
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.return_abnormalities.result", abnormalities)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAi(CommandSourceStack source) {
        int employees = ExhibitionSafetyService.resetAllAiNavigation(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.reset_ai.result", employees)
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int cleanupEvents(CommandSourceStack source) {
        ExhibitionSafetyService.EventCleanupReport report =
                ExhibitionSafetyService.cleanupEventEntities(source.getServer());
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.cleanup_events.result",
                report.ordealTargets(), report.eventEntities())
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int advanceGauge(CommandSourceStack source) {
        ExhibitionSafetyService.GaugeAdvanceReport report =
                ExhibitionSafetyService.advanceGaugeOne(source.getServer());
        if (!report.available()) {
            source.sendFailure(Component.translatable(
                "command.lobotocraft.exhibition.gauge.unavailable"));
            return 0;
        }
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.translatable(report.stagesAdvanced() > 0
                        ? "command.lobotocraft.exhibition.gauge.advanced"
                        : "command.lobotocraft.exhibition.gauge.result",
                report.gauge(), report.threshold())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int maxAi(CommandSourceStack source) {
        int employees = ExhibitionSafetyService.maxAllAiEmployees(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.max_ai.result", employees)
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static void sendRecoveryReport(CommandSourceStack source,
                                           ExhibitionSafetyService.RecoveryReport report) {
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.exhibition.recovery_report",
                report.players(), report.works(), report.orders(), report.abnormalities(),
                report.ordealTargets(), report.meltdowns(), report.executions(),
                report.resetEmployees())
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static MutableComponent button(String labelKey, String command,
                                           ChatFormatting color, String hoverKey) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hoverKey))));
    }
}
