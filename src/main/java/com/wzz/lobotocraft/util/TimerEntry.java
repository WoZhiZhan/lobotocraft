package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public abstract class TimerEntry {
    private static final Map<Class<? extends TimerEntry>, Map<UUID, TimerEntry>> globalEntityEntryMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<UUID, Boolean>> GLOBAL_CLASS_ENTITY_LOCK = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService scheduler = createScheduler();

    private final Map<UUID, ScheduledFuture<?>> timerMap = new ConcurrentHashMap<>();
    private int executions;
    // 默认让回调在主线程执行:计时调度仍在异步线程,但 onRunning/onEnd 切回主线程,
    // 避免在异步线程访问世界随机源、生成实体、修改实体状态等引发
    // "Accessing LegacyRandomSource from multiple threads" 之类的并发崩溃。
    private boolean requireMainThread = true;

    private static ScheduledExecutorService createScheduler() {
        return Executors.newScheduledThreadPool(
                Math.max(6, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread thread = new Thread(r, "SkillTimerThread");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    private static synchronized ScheduledExecutorService getScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = createScheduler();
        }
        return scheduler;
    }

    public void setRequireMainThread(boolean require) {
        this.requireMainThread = require;
    }

    public boolean isRequireMainThread() {
        return requireMainThread;
    }

    public boolean addSkillTimer(LivingEntity living, int delay, int duration, int executionsPerSecond, boolean refresh) {
        if (living == null) {
            return false;
        }
        UUID uuid = living.getUUID();
        if (refresh && timerMap.containsKey(uuid)) {
            removeTimer(uuid);
            if (timerMap.containsKey(uuid)) {
                return false;
            }
        }
        return this.addSkillTimer(living, delay, duration, executionsPerSecond);
    }

    public boolean addSkillTimer(LivingEntity living, int delay, int duration, int executionsPerSecond) {
        if (living == null) {
            return false;
        }
        UUID uuid = living.getUUID();
        Class<?> timerClass = this.getClass();
        Map<UUID, Boolean> classLock = GLOBAL_CLASS_ENTITY_LOCK
                .computeIfAbsent(timerClass, k -> new ConcurrentHashMap<>());
        if (classLock.putIfAbsent(uuid, true) != null) {
            ModLogger.getLogger().debug("实体 {} 已有 {} 类型的计时器", uuid, this.getTimerIdentifier());
            return false;
        }
        if (timerMap.containsKey(uuid)) {
            classLock.remove(uuid);
            return false;
        }
        if (delay < 0 || duration <= 0 || executionsPerSecond <= 0) {
            ModLogger.getLogger().warn("Invalid timer parameters: delay={}, duration={}, executionsPerSecond={}",
                    delay, duration, executionsPerSecond);
            return false;
        }
        ScheduledExecutorService currentScheduler = getScheduler();
        Class<? extends TimerEntry> entryClass = this.getClass();
        globalEntityEntryMap.computeIfAbsent(entryClass, k -> new ConcurrentHashMap<>());
        Map<UUID, TimerEntry> entryMap = globalEntityEntryMap.get(entryClass);
        synchronized (this) {
            if (entryMap.containsKey(uuid) || timerMap.containsKey(uuid)) {
                return false;
            }
            entryMap.put(uuid, this);
        }
        onStart(living);
        try {
            long period = Math.max(1, 1000L / executionsPerSecond);
            ScheduledFuture<?> skillTask = currentScheduler.scheduleAtFixedRate(
                    () -> executeTaskSafely(living, uuid),
                    delay,
                    period,
                    TimeUnit.MILLISECONDS
            );
            currentScheduler.schedule(
                    () -> stopTimerSafely(living, uuid),
                    delay + duration,
                    TimeUnit.MILLISECONDS
            );
            timerMap.put(uuid, skillTask);
            return true;
        } catch (RejectedExecutionException e) {
            ModLogger.getLogger().error("Failed to schedule skill timer", e);
            entryMap.remove(uuid);
            classLock.remove(uuid);
            return false;
        } catch (Exception e) {
            ModLogger.getLogger().error("Unexpected error while scheduling timer", e);
            entryMap.remove(uuid);
            classLock.remove(uuid);
            return false;
        }
    }

    public int getExecutions() {
        return executions;
    }

    public long getRemainingTime(UUID entityId, TimeUnit timeUnit) {
        ScheduledFuture<?> task = timerMap.get(entityId);
        if (task == null || task.isDone() || task.isCancelled()) {
            return -1;
        }
        try {
            long remainingDelay = task.getDelay(timeUnit);
            return Math.max(0, remainingDelay);
        } catch (Exception e) {
            ModLogger.getLogger().warn("Failed to get remaining time for entity {}", entityId, e);
            return -1;
        }
    }

    public long getRemainingTime(UUID entityId) {
        return getRemainingTime(entityId, TimeUnit.MILLISECONDS);
    }

    // 检查是否还剩指定毫秒数内结束
    public boolean isEndingWithin(UUID entityId, long milliseconds) {
        long remaining = getRemainingTime(entityId);
        if (remaining < 0) {
            return false;
        }
        return remaining <= milliseconds;
    }

    private void executeTaskSafely(LivingEntity living, UUID entityId) {
        try {
            if (isEntityValid(living)) {
                this.executions++;
                if (requireMainThread) {
                    ThreadExecutor.executeOnMainThread(living, () -> {
                        try {
                            onRunning(living);
                        } catch (Exception e) {
                            ModLogger.getLogger().error("Skill task error on main thread", e);
                            removeTimer(entityId);
                        }
                    });
                } else {
                    onRunning(living);
                }
            } else {
                removeTimer(entityId);
            }
        } catch (Exception e) {
            ModLogger.getLogger().error("Skill task error", e);
            removeTimer(entityId);
        }
    }

    private void stopTimerSafely(LivingEntity living, UUID entityId) {
        Runnable stopTask = () -> {
            try {
                if (isEntityValid(living)) {
                    onEnd(living);
                }
            } finally {
                removeTimer(entityId);
            }
        };
        if (requireMainThread && living != null) {
            ThreadExecutor.executeOnMainThread(living, stopTask);
        } else {
            stopTask.run();
        }
    }

    private boolean isEntityValid(@Nullable LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    public synchronized void removeTimer(UUID livingId) {
        ScheduledFuture<?> task = timerMap.remove(livingId);
        if (task != null) {
            task.cancel(false);
        }
        Map<UUID, TimerEntry> entryMap = globalEntityEntryMap.get(this.getClass());
        if (entryMap != null) {
            entryMap.remove(livingId);
            GLOBAL_CLASS_ENTITY_LOCK.getOrDefault(this.getClass(), Collections.emptyMap())
                    .remove(livingId);
        }
    }

    public static void shutdown(LivingEntity living) {
        if (living == null) {
            ModLogger.getLogger().warn("shutdown fail! living is null");
            return;
        }
        UUID uuid = living.getUUID();
        for (Map<UUID, TimerEntry> timers : globalEntityEntryMap.values()) {
            TimerEntry timer = timers.get(uuid);
            if (timer != null) {
                timer.stopTimerSafely(living, uuid);
            }
        }
    }

    public static void shutdownAll() {
        synchronized (TimerEntry.class) {
            if (scheduler != null && !scheduler.isShutdown()) {
                ModLogger.getLogger().info("Shutting down TimerEntry scheduler");
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                        if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                            ModLogger.getLogger().warn("Scheduler did not terminate cleanly");
                        }
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            for (Map<UUID, TimerEntry> timers : globalEntityEntryMap.values()) {
                for (TimerEntry timer : timers.values()) {
                    for (UUID uuid : new ArrayList<>(timer.timerMap.keySet())) {
                        timer.removeTimer(uuid);
                    }
                }
            }
            globalEntityEntryMap.clear();
        }
    }

    public static synchronized void reinitializeScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            ModLogger.getLogger().info("Scheduler is still running, skipping reinitialization");
            return;
        }
        scheduler = createScheduler();
        ModLogger.getLogger().info("Scheduler reinitialized");
    }

    public static boolean isSchedulerRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    public static boolean isEntityBound(Class<? extends TimerEntry> entryClass, UUID uuid) {
        Map<UUID, TimerEntry> map = globalEntityEntryMap.get(entryClass);
        return map != null && map.containsKey(uuid);
    }

    private String getTimerIdentifier() {
        Class<?> clazz = this.getClass();
        return clazz.isAnonymousClass() ?
                "Anonymous@" + Integer.toHexString(clazz.hashCode()) :
                clazz.getSimpleName();
    }

    public void onStart(@NotNull LivingEntity living) {}
    public void onRunning(@NotNull LivingEntity living) {}
    public void onEnd(@NotNull LivingEntity living) {}
}