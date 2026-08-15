package com.wzz.lobotocraft.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor.PerformanceSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only diagnostics. It is deliberately separate from the server command tree. */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientDebugCommand {
    private ClientDebugCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lobotocraft-client")
                .then(Commands.literal("performance")
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("dump")
                                .executes(context -> dump(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> reset(context.getSource())))
                        .then(Commands.literal("path")
                                .executes(context -> path(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        PerformanceSnapshot snapshot = ClientPerformanceMonitor.snapshot();
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.summary.timing",
                snapshot.slowFrames(), snapshot.slowTicks(), snapshot.maxFrameMillis(),
                snapshot.maxTickMillis(), snapshot.droppedReports()), false);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.summary.world",
                snapshot.fps(), snapshot.entities(), snapshot.chunks(), snapshot.screen(),
                snapshot.dimension(), snapshot.currentSection(), snapshot.sectionCount()), false);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.report_path",
                ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }

    private static int dump(CommandSourceStack source) {
        ClientPerformanceMonitor.requestSummary();
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.dump_queued",
                ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ClientPerformanceMonitor.resetCounters();
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.reset"), false);
        return 1;
    }

    private static int path(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft.client_performance.report",
                ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }
}
