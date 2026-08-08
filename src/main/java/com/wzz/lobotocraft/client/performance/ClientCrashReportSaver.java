package com.wzz.lobotocraft.client.performance;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/** Persists the report before recovery mods replace Minecraft's normal crash screen. */
public final class ClientCrashReportSaver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private ClientCrashReportSaver() {
    }

    public static void save(Minecraft minecraft, Throwable throwable) {
        if (minecraft == null || throwable == null) return;
        try {
            CrashReport report = throwable instanceof ReportedException reported
                    ? reported.getReport()
                    : CrashReport.forThrowable(throwable, "Unexpected client error");
            File directory = new File(minecraft.gameDirectory, "crash-reports");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                LOGGER.error("Could not create client crash report directory {}", directory);
                return;
            }
            String stamp = FILE_TIME.format(LocalDateTime.now());
            File destination = uniqueFile(directory, "crash-" + stamp + "-client", ".txt");
            if (report.saveToFile(destination)) {
                LOGGER.error("Saved complete recovered client crash report to {}", destination.getAbsolutePath());
            }
        } catch (Throwable saveFailure) {
            LOGGER.error("Failed to persist recovered client crash report", saveFailure);
        }
    }

    public static String describe(Throwable throwable) {
        if (throwable == null) return "Unknown client crash";
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (depth > 0) result.append("\nCaused by: ");
            result.append(current);
            StackTraceElement[] stack = current.getStackTrace();
            for (int index = 0; index < Math.min(8, stack.length); index++) {
                result.append("\n    at ").append(stack[index]);
            }
        }
        result.append("\n\n完整报告已保存到 crash-reports 目录。");
        return result.toString();
    }

    private static File uniqueFile(File directory, String baseName, String extension) {
        File candidate = new File(directory, baseName + extension);
        for (int suffix = 1; candidate.exists(); suffix++) {
            candidate = new File(directory, baseName + "-" + suffix + extension);
        }
        return candidate;
    }
}
