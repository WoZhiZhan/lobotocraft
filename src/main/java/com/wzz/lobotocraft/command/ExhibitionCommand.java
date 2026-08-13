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
        source.sendSuccess(() -> Component.literal("=== 脑叶公司 展会控制 ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("当前状态：")
                .append(Component.literal(enabled ? "保护已开启" : "保护未开启")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("  已恢复 " + recoveries + " 次")
                        .withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> button("[开始展会]", ROOT + "start", ChatFormatting.GREEN,
                        "开启保护、恢复现场并立即保存")
                .append(Component.literal("  "))
                .append(button("[一键恢复]", ROOT + "recover", ChatFormatting.YELLOW,
                        "出现卡住、失控或混乱时点击"))
                .append(Component.literal("  "))
                .append(button("[结束展会]", ROOT + "stop", ChatFormatting.RED,
                        "关闭自动保护")), false);
        source.sendSuccess(() -> button("[停止工作]", ROOT + "stop-work", ChatFormatting.YELLOW,
                        "停止玩家工作、AI 工作和当前 AI 命令")
                .append(Component.literal("  "))
                .append(button("[清除控制]", ROOT + "clear-status", ChatFormatting.AQUA,
                        "解除恐慌、魅惑、冰封等控制并恢复生命精神"))
                .append(Component.literal("  "))
                .append(button("[回收异想体]", ROOT + "return-abnormalities", ChatFormatting.GREEN,
                        "让所有出逃异想体返回收容室")), false);
        source.sendSuccess(() -> button("[恢复AI寻路]", ROOT + "reset-ai", ChatFormatting.AQUA,
                        "清空卡住的 AI 状态和路线，让员工重新规划")
                .append(Component.literal("  "))
                .append(button("[清理事件]", ROOT + "cleanup-events", ChatFormatting.RED,
                        "终止考验并清理残留事件实体"))
                .append(Component.literal("  "))
                .append(button("[逆流+1]", ROOT + "gauge", ChatFormatting.LIGHT_PURPLE,
                        "按一次普通工作增加一格逆流进度"))
                .append(Component.literal("  "))
                .append(button("[AI员工满级]", ROOT + "max-ai", ChatFormatting.GOLD,
                        "将所有已招募 AI 员工四项属性设为 110")), false);
        source.sendSuccess(() -> Component.literal(
                "保护开启时：自动考验与自动熔毁暂停；手动演示、工作和出逃仍可使用。")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        ExhibitionSafetyService.setEnabled(source.getServer(), true);
        ExhibitionSafetyService.RecoveryReport report = ExhibitionSafetyService.recover(source.getServer());
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("展会保护已开启，现场已恢复并保存。")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        sendRecoveryReport(source, report);
        return sendPanel(source);
    }

    private static int recover(CommandSourceStack source) {
        ExhibitionSafetyService.RecoveryReport report = ExhibitionSafetyService.recover(source.getServer());
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("一键恢复完成，可以继续展示。")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        sendRecoveryReport(source, report);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ExhibitionSafetyService.setEnabled(source.getServer(), false);
        source.getServer().saveEverything(false, true, true);
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("展会保护已关闭。")
                        .withStyle(ChatFormatting.YELLOW), false);
        return sendPanel(source);
    }

    private static int stopWork(CommandSourceStack source) {
        ExhibitionSafetyService.WorkStopReport report =
                ExhibitionSafetyService.stopAllWork(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "已停止工作 " + report.works() + " 个，AI 命令 " + report.orders() + " 个。")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int clearStatus(CommandSourceStack source) {
        int players = ExhibitionSafetyService.clearAllControlStates(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "已清除 " + players + " 名人员的控制状态，并恢复生命与精神。")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int returnAbnormalities(CommandSourceStack source) {
        int abnormalities = ExhibitionSafetyService.returnAllAbnormalities(source.getServer());
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.literal(
                "已回收 " + abnormalities + " 个出逃异想体。")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAi(CommandSourceStack source) {
        int employees = ExhibitionSafetyService.resetAllAiNavigation(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "已重置 " + employees + " 名 AI 员工的命令、战斗状态与寻路。")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int cleanupEvents(CommandSourceStack source) {
        ExhibitionSafetyService.EventCleanupReport report =
                ExhibitionSafetyService.cleanupEventEntities(source.getServer());
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.literal(
                "已清理考验实体 " + report.ordealTargets()
                        + " 个，事件实体 " + report.eventEntities() + " 个。")
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int advanceGauge(CommandSourceStack source) {
        ExhibitionSafetyService.GaugeAdvanceReport report =
                ExhibitionSafetyService.advanceGaugeOne(source.getServer());
        if (!report.available()) {
            source.sendFailure(Component.literal("未找到脑叶公司设施维度，无法增加逆流进度。"));
            return 0;
        }
        source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.literal(
                "逆流进度已按一次普通工作增加 1 格；当前 "
                        + report.gauge() + "/" + report.threshold()
                        + (report.stagesAdvanced() > 0 ? "，已正常推进一个警报等级。" : "。"))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int maxAi(CommandSourceStack source) {
        int employees = ExhibitionSafetyService.maxAllAiEmployees(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "已将 " + employees + " 名 AI 员工的四项属性设为 110。")
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static void sendRecoveryReport(CommandSourceStack source,
                                           ExhibitionSafetyService.RecoveryReport report) {
        source.sendSuccess(() -> Component.literal(
                "恢复结果：人员 " + report.players()
                        + "，工作 " + report.works()
                        + "，AI命令 " + report.orders()
                        + "，出逃异想体 " + report.abnormalities()
                        + "，考验实体 " + report.ordealTargets()
                        + "，熔毁 " + report.meltdowns()
                        + "，拥抱处决 " + report.executions()
                        + "，AI重置 " + report.resetEmployees())
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static MutableComponent button(String label, String command, ChatFormatting color, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(hover))));
    }
}
