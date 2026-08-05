package com.wzz.lobotocraft.client.performance;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ShockwaveEffectManager;
import com.wzz.lobotocraft.client.renderer.ExplosionRenderer;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-overhead client watchdog. Normal frames only update primitive counters; reports are formatted
 * and written by daemon threads after a slow frame or tick has been observed.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class ClientPerformanceMonitor {
    private static final String FRAME_OPERATION = "render_frame";
    private static final String TICK_OPERATION = "client_tick";
    private static final int CONTEXT_REFRESH_TICKS = 20;
    private static final int MAX_STACK_FRAMES = 24;
    private static final int MAX_QUEUED_REPORTS = 32;
    private static final long WATCHDOG_INTERVAL_MILLIS = 50L;
    private static final long SUMMARY_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final Path REPORT_PATH = FMLPaths.GAMEDIR.get()
            .resolve("logs")
            .resolve("lobotocraft-client-performance.log");
    private static final ArrayBlockingQueue<ReportItem> REPORT_QUEUE =
            new ArrayBlockingQueue<>(MAX_QUEUED_REPORTS);
    private static final Map<String, SectionTiming> SECTION_TIMINGS = new ConcurrentHashMap<>();
    private static final List<GarbageCollectorMXBean> GC_BEANS =
            List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
    private static final AtomicLong DROPPED_REPORTS = new AtomicLong();
    private static final AtomicLong SLOW_FRAMES = new AtomicLong();
    private static final AtomicLong SLOW_TICKS = new AtomicLong();
    private static final AtomicLong MAX_FRAME_NANOS = new AtomicLong();
    private static final AtomicLong MAX_TICK_NANOS = new AtomicLong();
    private static final AtomicLong[] LAST_REPORT_NANOS = {
            new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()
    };

    private static volatile Thread clientThread;
    private static volatile long frameStartNanos;
    private static volatile long tickStartNanos;
    private static volatile int frameReportedSeverity;
    private static volatile int tickReportedSeverity;
    private static volatile String currentSection = "none";
    private static volatile ContextSnapshot lastContext = ContextSnapshot.EMPTY;
    private static volatile long nextSummaryNanos = System.nanoTime() + SUMMARY_INTERVAL_NANOS;
    private static int contextRefreshCountdown;

    static {
        Thread writer = new Thread(ClientPerformanceMonitor::writerLoop,
                "Lobotocraft performance report writer");
        writer.setDaemon(true);
        writer.setPriority(Thread.MIN_PRIORITY);
        writer.start();

        Thread watchdog = new Thread(ClientPerformanceMonitor::watchdogLoop,
                "Lobotocraft client watchdog");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.start();

        enqueue("[" + Instant.now() + "] monitor=START thresholds_ms=100/250/1000 report="
                + REPORT_PATH.toAbsolutePath() + System.lineSeparator());
        ModLogger.LOGGER.info("Client performance monitor enabled; reports: {}",
                REPORT_PATH.toAbsolutePath());
    }

    private ClientPerformanceMonitor() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        markClientThread();
        tickReportedSeverity = ClientPerformancePolicy.NORMAL;
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long endNanos = System.nanoTime();
        long startedNanos = tickStartNanos;
        tickStartNanos = 0L;
        recordCompletedOperation(TICK_OPERATION, startedNanos, endNanos,
                tickReportedSeverity, SLOW_TICKS, MAX_TICK_NANOS);

        if (--contextRefreshCountdown <= 0) {
            contextRefreshCountdown = CONTEXT_REFRESH_TICKS;
            refreshContext();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        markClientThread();
        frameReportedSeverity = ClientPerformancePolicy.NORMAL;
        frameStartNanos = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long endNanos = System.nanoTime();
        long startedNanos = frameStartNanos;
        frameStartNanos = 0L;
        recordCompletedOperation(FRAME_OPERATION, startedNanos, endNanos,
                frameReportedSeverity, SLOW_FRAMES, MAX_FRAME_NANOS);
    }

    /** Begins a named hot-path section without allocating a scope object. */
    public static long beginSection(String name) {
        currentSection = name;
        return System.nanoTime();
    }

    /** Records the latest and worst duration for a named hot-path section. */
    public static void endSection(String name, long startedNanos) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedNanos);
        SECTION_TIMINGS.computeIfAbsent(name, ignored -> new SectionTiming()).record(elapsedNanos);
        if (name.equals(currentSection)) {
            currentSection = "none";
        }
    }

    /** Returns the current counters without consuming the periodic summary. */
    public static PerformanceSnapshot snapshot() {
        refreshContext();
        ContextSnapshot context = lastContext;
        return new PerformanceSnapshot(
                SLOW_FRAMES.get(),
                SLOW_TICKS.get(),
                nanosToMillis(MAX_FRAME_NANOS.get()),
                nanosToMillis(MAX_TICK_NANOS.get()),
                DROPPED_REPORTS.get(),
                currentSection,
                context.screen,
                context.dimension,
                context.fps,
                context.entities,
                context.chunks,
                SECTION_TIMINGS.size());
    }

    /** Queues an immediate diagnostic summary for the asynchronous writer. */
    public static void requestSummary() {
        submitSummary();
    }

    /** Clears accumulated counters and section timings, preserving the report file. */
    public static void resetCounters() {
        SLOW_FRAMES.set(0L);
        SLOW_TICKS.set(0L);
        MAX_FRAME_NANOS.set(0L);
        MAX_TICK_NANOS.set(0L);
        DROPPED_REPORTS.set(0L);
        SECTION_TIMINGS.clear();
        currentSection = "none";
        for (AtomicLong lastReport : LAST_REPORT_NANOS) {
            lastReport.set(0L);
        }
        nextSummaryNanos = System.nanoTime() + SUMMARY_INTERVAL_NANOS;
    }

    public static Path reportPath() {
        return REPORT_PATH.toAbsolutePath();
    }

    private static void markClientThread() {
        if (clientThread == null) {
            clientThread = Thread.currentThread();
        }
    }

    private static void recordCompletedOperation(String operation, long startedNanos, long endNanos,
                                                  int alreadyReportedSeverity, AtomicLong slowCounter,
                                                  AtomicLong maximum) {
        if (startedNanos <= 0L || endNanos < startedNanos) return;
        long elapsedNanos = endNanos - startedNanos;
        updateMaximum(maximum, elapsedNanos);
        int severity = ClientPerformancePolicy.classify(elapsedNanos);
        if (severity == ClientPerformancePolicy.NORMAL) return;

        slowCounter.incrementAndGet();
        if (severity > alreadyReportedSeverity) {
            submitSlowReport(operation, severity, elapsedNanos, Thread.currentThread().getStackTrace());
        }
    }

    private static void watchdogLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(WATCHDOG_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            long now = System.nanoTime();
            sampleActiveOperation(FRAME_OPERATION, frameStartNanos, true, now);
            sampleActiveOperation(TICK_OPERATION, tickStartNanos, false, now);
            if (now >= nextSummaryNanos) {
                nextSummaryNanos = now + SUMMARY_INTERVAL_NANOS;
                submitSummary();
            }
        }
    }

    private static void sampleActiveOperation(String operation, long startedNanos,
                                              boolean frame, long nowNanos) {
        if (startedNanos <= 0L || nowNanos <= startedNanos) return;
        long elapsedNanos = nowNanos - startedNanos;
        int severity = ClientPerformancePolicy.classify(elapsedNanos);
        if (severity == ClientPerformancePolicy.NORMAL) return;

        int reported = frame ? frameReportedSeverity : tickReportedSeverity;
        if (severity <= reported) return;
        if (frame) {
            frameReportedSeverity = severity;
        } else {
            tickReportedSeverity = severity;
        }

        Thread thread = clientThread;
        StackTraceElement[] stack = thread == null ? new StackTraceElement[0] : thread.getStackTrace();
        submitSlowReport(operation, severity, elapsedNanos, stack);
    }

    private static void submitSlowReport(String operation, int severity, long elapsedNanos,
                                         StackTraceElement[] stack) {
        if (!acquireReportPermit(severity, System.nanoTime())) return;
        enqueue(new SlowReport(operation, severity, elapsedNanos, currentSection, lastContext, stack));
    }

    private static boolean acquireReportPermit(int severity, long nowNanos) {
        AtomicLong lastReport = LAST_REPORT_NANOS[severity];
        long previous = lastReport.get();
        long throttle = ClientPerformancePolicy.reportThrottleNanos(severity);
        while (nowNanos - previous >= throttle) {
            if (lastReport.compareAndSet(previous, nowNanos)) return true;
            previous = lastReport.get();
        }
        return false;
    }

    private static String buildReport(String operation, int severity, long elapsedNanos,
                                      String section, ContextSnapshot context,
                                      StackTraceElement[] stack) {
        StringBuilder builder = new StringBuilder(2_048);
        builder.append('[').append(Instant.now()).append("] severity=")
                .append(ClientPerformancePolicy.name(severity))
                .append(" operation=").append(operation)
                .append(" elapsed_ms=").append(nanosToMillis(elapsedNanos))
                .append(" current_section=").append(section).append('\n');
        appendContext(builder, context);
        appendMemoryAndGc(builder);
        appendSections(builder);
        builder.append("client_thread_stack:").append('\n');
        int frames = Math.min(stack.length, MAX_STACK_FRAMES);
        for (int i = 0; i < frames; i++) {
            builder.append("  at ").append(stack[i]).append('\n');
        }
        if (stack.length > frames) {
            builder.append("  ... ").append(stack.length - frames).append(" more").append('\n');
        }
        builder.append('\n');
        return builder.toString();
    }

    private static void submitSummary() {
        long slowFrames = SLOW_FRAMES.getAndSet(0L);
        long slowTicks = SLOW_TICKS.getAndSet(0L);
        long maxFrame = MAX_FRAME_NANOS.getAndSet(0L);
        long maxTick = MAX_TICK_NANOS.getAndSet(0L);
        StringBuilder builder = new StringBuilder(1_024);
        builder.append('[').append(Instant.now()).append("] summary period_seconds=60")
                .append(" slow_frames=").append(slowFrames)
                .append(" slow_ticks=").append(slowTicks)
                .append(" max_frame_ms=").append(nanosToMillis(maxFrame))
                .append(" max_tick_ms=").append(nanosToMillis(maxTick))
                .append(" dropped_reports=").append(DROPPED_REPORTS.get()).append('\n');
        appendContext(builder, lastContext);
        appendMemoryAndGc(builder);
        appendSections(builder);
        builder.append('\n');
        enqueue(builder.toString());
    }

    private static void refreshContext() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            String screen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getName();
            String dimension = level == null ? "none" : level.dimension().location().toString();
            double x = minecraft.player == null ? Double.NaN : minecraft.player.getX();
            double y = minecraft.player == null ? Double.NaN : minecraft.player.getY();
            double z = minecraft.player == null ? Double.NaN : minecraft.player.getZ();
            int entities = level == null ? 0 : level.getEntityCount();
            int chunks = level == null ? 0 : level.getChunkSource().getLoadedChunksCount();
            PostChain effect = minecraft.gameRenderer.currentEffect();
            String shader = effect == null ? "none" : effect.getName();
            lastContext = new ContextSnapshot(screen, dimension, x, y, z, minecraft.getFps(),
                    entities, chunks, ShockwaveEffectManager.getActiveShockwaveCount(),
                    ExplosionRenderer.getActiveExplosionCount(), shader, minecraft.isPaused(),
                    minecraft.isWindowActive());
        } catch (RuntimeException exception) {
            lastContext = ContextSnapshot.unavailable(exception.getClass().getSimpleName());
        }
    }

    private static void appendContext(StringBuilder builder, ContextSnapshot context) {
        builder.append("context: screen=").append(context.screen)
                .append(" dimension=").append(context.dimension)
                .append(" position=");
        if (Double.isFinite(context.x)) {
            builder.append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f", context.x, context.y, context.z));
        } else {
            builder.append("none");
        }
        builder.append(" fps=").append(context.fps)
                .append(" entities=").append(context.entities)
                .append(" chunks=").append(context.chunks)
                .append(" shockwaves=").append(context.shockwaves)
                .append(" explosions=").append(context.explosions)
                .append(" shader=").append(context.shader)
                .append(" paused=").append(context.paused)
                .append(" window_active=").append(context.windowActive).append('\n');
    }

    private static void appendMemoryAndGc(StringBuilder builder) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        builder.append("jvm: used_mib=").append(bytesToMib(used))
                .append(" committed_mib=").append(bytesToMib(runtime.totalMemory()))
                .append(" max_mib=").append(bytesToMib(runtime.maxMemory()))
                .append(" gc=");
        for (int i = 0; i < GC_BEANS.size(); i++) {
            GarbageCollectorMXBean bean = GC_BEANS.get(i);
            if (i > 0) builder.append(';');
            builder.append(bean.getName()).append('(')
                    .append(bean.getCollectionCount()).append('/')
                    .append(bean.getCollectionTime()).append("ms)");
        }
        builder.append('\n');
    }

    private static void appendSections(StringBuilder builder) {
        if (SECTION_TIMINGS.isEmpty()) {
            builder.append("sections: none").append('\n');
            return;
        }
        List<Map.Entry<String, SectionTiming>> sections = new ArrayList<>(SECTION_TIMINGS.entrySet());
        sections.sort(Comparator.comparing(Map.Entry::getKey));
        builder.append("sections:").append('\n');
        for (Map.Entry<String, SectionTiming> entry : sections) {
            SectionTiming timing = entry.getValue();
            builder.append("  ").append(entry.getKey())
                    .append(" last_ms=").append(nanosToMillis(timing.lastNanos.get()))
                    .append(" max_ms=").append(nanosToMillis(timing.maxNanos.get()))
                    .append(" samples=").append(timing.samples.get()).append('\n');
        }
    }

    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }

    private static long bytesToMib(long bytes) {
        return Math.max(0L, bytes) / (1024L * 1024L);
    }

    private static void updateMaximum(AtomicLong maximum, long candidate) {
        long previous = maximum.get();
        while (candidate > previous && !maximum.compareAndSet(previous, candidate)) {
            previous = maximum.get();
        }
    }

    private static void enqueue(String report) {
        enqueue(new TextReport(report));
    }

    private static void enqueue(ReportItem report) {
        if (!REPORT_QUEUE.offer(report)) {
            DROPPED_REPORTS.incrementAndGet();
        }
    }

    private static void writerLoop() {
        try {
            Files.createDirectories(REPORT_PATH.getParent());
        } catch (IOException exception) {
            ModLogger.LOGGER.error("Unable to create client performance report directory {}",
                    REPORT_PATH.getParent(), exception);
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(REPORT_PATH, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (!Thread.currentThread().isInterrupted()) {
                ReportItem report;
                try {
                    report = REPORT_QUEUE.take();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                writer.write(report.format());
                writer.flush();
            }
        } catch (IOException exception) {
            ModLogger.LOGGER.error("Client performance report writer stopped for {}", REPORT_PATH, exception);
        }
    }

    private static final class SectionTiming {
        private final AtomicLong lastNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicLong samples = new AtomicLong();

        private void record(long elapsedNanos) {
            lastNanos.set(elapsedNanos);
            samples.incrementAndGet();
            updateMaximum(maxNanos, elapsedNanos);
        }
    }

    /** Stable, command-facing view of the monitor; no client-only game objects escape it. */
    public record PerformanceSnapshot(long slowFrames, long slowTicks, long maxFrameMillis,
                                      long maxTickMillis, long droppedReports, String currentSection,
                                      String screen, String dimension, int fps, int entities,
                                      int chunks, int sectionCount) {
    }

    private interface ReportItem {
        String format();
    }

    private record TextReport(String report) implements ReportItem {
        @Override
        public String format() {
            return report;
        }
    }

    private record SlowReport(String operation, int severity, long elapsedNanos, String section,
                              ContextSnapshot context, StackTraceElement[] stack) implements ReportItem {
        @Override
        public String format() {
            return buildReport(operation, severity, elapsedNanos, section, context, stack);
        }
    }

    private record ContextSnapshot(String screen, String dimension, double x, double y, double z,
                                   int fps, int entities, int chunks, int shockwaves, int explosions,
                                   String shader, boolean paused, boolean windowActive) {
        private static final ContextSnapshot EMPTY = unavailable("not_sampled");

        private static ContextSnapshot unavailable(String reason) {
            return new ContextSnapshot(reason, "unknown", Double.NaN, Double.NaN, Double.NaN,
                    0, 0, 0, 0, 0, "unknown", false, true);
        }
    }
}
