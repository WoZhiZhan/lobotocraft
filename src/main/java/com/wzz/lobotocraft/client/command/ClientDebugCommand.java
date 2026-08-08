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

import java.util.Locale;

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
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[性能] slow_frames=%d slow_ticks=%d max_frame=%dms max_tick=%dms dropped=%d",
                snapshot.slowFrames(), snapshot.slowTicks(), snapshot.maxFrameMillis(),
                snapshot.maxTickMillis(), snapshot.droppedReports())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[性能] fps=%d entities=%d chunks=%d screen=%s dimension=%s section=%s (%d sections)",
                snapshot.fps(), snapshot.entities(), snapshot.chunks(), snapshot.screen(),
                snapshot.dimension(), snapshot.currentSection(), snapshot.sectionCount())), false);
        source.sendSuccess(() -> Component.literal("报告文件：" + ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }

    private static int dump(CommandSourceStack source) {
        ClientPerformanceMonitor.requestSummary();
        source.sendSuccess(() -> Component.literal(
                "已排队写入性能摘要：" + ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ClientPerformanceMonitor.resetCounters();
        source.sendSuccess(() -> Component.literal("客户端性能计数器与分段统计已清零。"), false);
        return 1;
    }

    private static int path(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "客户端性能报告：" + ClientPerformanceMonitor.reportPath()), false);
        return 1;
    }
}
