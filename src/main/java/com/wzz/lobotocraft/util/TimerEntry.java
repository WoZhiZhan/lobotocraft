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

public abstract class TimerEntry<T extends LivingEntity> {
    private static final Map<Class<? extends TimerEntry<?>>, Map<UUID, TimerEntry<?>>> globalEntityEntryMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<UUID, Boolean>> GLOBAL_CLASS_ENTITY_LOCK = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService scheduler = createScheduler();

    private final Map<UUID, TimerState<T>> timerMap = new ConcurrentHashMap<>();
    private int executions;
    // 默认让回调在主线程执行:计时调度仍在异步线程,但 onRunning/onEnd 切回主线程,
    // 避免在异步线程访问世界随机源、生成实体、修改实体状态等引发
    // "Accessing LegacyRandomSource from multiple threads" 之类的并发崩溃。
    private boolean requireMainThread = true;

    /** Why a timer ended before its normal completion callback. */
    public enum CancellationReason {
        EXPLICIT,
        REFRESH,
        ENTITY_INVALID,
        ERROR,
        START_FAILURE,
        LOGOUT
    }

    /**
     * One identity token owns both scheduled tasks. Using the state object's
     * identity instead of a per-UUID generation map makes stale callbacks
     * inert without retaining every UUID ever seen by the server.
     */
    private static final class TimerState<E extends LivingEntity> {
        private final E entity;
        private ScheduledFuture<?> repeatingFuture;
        private final long endGameTick;
        private final long intervalTicks;
        private long nextExecutionTick;
        private boolean cancelled;

        private TimerState(E entity, int delayMs, int durationMs, int executionsPerSecond) {
            this.entity = entity;
            long now = entity.level().getGameTime();
            long delayTicks = millisecondsToTicks(delayMs);
            long durationTicks = Math.max(1L, millisecondsToTicks(durationMs));
            this.nextExecutionTick = now + delayTicks;
            this.endGameTick = this.nextExecutionTick + durationTicks;
            this.intervalTicks = Math.max(1L, Math.round(20.0D / executionsPerSecond));
        }

        private synchronized void setRepeatingFuture(ScheduledFuture<?> future) {
            if (cancelled) future.cancel(false);
            else repeatingFuture = future;
        }

        private synchronized void cancelFutures() {
            cancelled = true;
            if (repeatingFuture != null) repeatingFuture.cancel(false);
        }

        private synchronized long getRemainingDelay(TimeUnit unit) {
            if (cancelled) return -1L;
            long remainingTicks = Math.max(0L, endGameTick - entity.level().getGameTime());
            return unit.convert(remainingTicks * 50L, TimeUnit.MILLISECONDS);
        }

        private synchronized boolean isDue(long gameTime) {
            if (cancelled || gameTime < nextExecutionTick) return false;
            nextExecutionTick = gameTime + intervalTicks;
            return true;
        }
    }

    private static long millisecondsToTicks(long milliseconds) {
        return Math.max(0L, (milliseconds + 49L) / 50L);
    }

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

    public boolean addSkillTimer(T entity, int delay, int duration, int executionsPerSecond, boolean refresh) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        if (refresh) {
            @SuppressWarnings("unchecked")
            Class<? extends TimerEntry<?>> timerClass = (Class<? extends TimerEntry<?>>) getClass();
            TimerEntry<?> existing = globalEntityEntryMap
                    .getOrDefault(timerClass, Collections.emptyMap()).get(uuid);
            if (existing != null) existing.cancelTimer(uuid, CancellationReason.REFRESH, true);
        }
        return this.addSkillTimer(entity, delay, duration, executionsPerSecond);
    }

    public boolean addSkillTimer(T entity, int delay, int duration, int executionsPerSecond) {
        if (entity == null) {
            return false;
        }
        if (delay < 0 || duration <= 0 || executionsPerSecond <= 0) {
            ModLogger.getLogger().warn("Invalid timer parameters: delay={}, duration={}, executionsPerSecond={}",
                    delay, duration, executionsPerSecond);
            return false;
        }
        UUID uuid = entity.getUUID();
        Class<?> timerClass = this.getClass();
        Map<UUID, Boolean> classLock = GLOBAL_CLASS_ENTITY_LOCK
                .computeIfAbsent(timerClass, k -> new ConcurrentHashMap<>());
        if (classLock.putIfAbsent(uuid, true) != null) {
            ModLogger.getLogger().debug("实体 {} 已有 {} 类型的计时器", uuid, this.getTimerIdentifier());
            return false;
        }
        ScheduledExecutorService currentScheduler = getScheduler();
        @SuppressWarnings("unchecked")
        Class<? extends TimerEntry<?>> entryClass = (Class<? extends TimerEntry<?>>) this.getClass();
        globalEntityEntryMap.computeIfAbsent(entryClass, k -> new ConcurrentHashMap<>());
        Map<UUID, TimerEntry<?>> entryMap = globalEntityEntryMap.get(entryClass);
        TimerState<T> state = new TimerState<>(entity, delay, duration, executionsPerSecond);
        if (timerMap.putIfAbsent(uuid, state) != null) {
            classLock.remove(uuid, true);
            return false;
        }
        entryMap.put(uuid, this);
        try {
            onStart(entity);
            if (!isCurrent(uuid, state)) return false;
            ScheduledFuture<?> skillTask = currentScheduler.scheduleAtFixedRate(
                    () -> executeTaskSafely(uuid, state),
                    0L,
                    50L,
                    TimeUnit.MILLISECONDS
            );
            state.setRepeatingFuture(skillTask);
            return true;
        } catch (Exception e) {
            cancelState(uuid, state, CancellationReason.START_FAILURE, true);
            ModLogger.getLogger().error("Failed to start skill timer", e);
            return false;
        }
    }

    public int getExecutions() {
        return executions;
    }

    public long getRemainingTime(UUID entityId, TimeUnit timeUnit) {
        TimerState<T> state = timerMap.get(entityId);
        if (state == null) return -1;
        try {
            return state.getRemainingDelay(timeUnit);
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

    private void executeTaskSafely(UUID entityId, TimerState<T> state) {
        try {
            if (!isCurrent(entityId, state)) return;
            T entity = state.entity;
            if (isEntityValid(entity)) {
                long gameTime = entity.level().getGameTime();
                if (gameTime >= state.endGameTick) {
                    stopTimerSafely(entityId, state);
                    return;
                }
                if (!state.isDue(gameTime)) return;
                this.executions++;
                if (requireMainThread) {
                    ThreadExecutor.executeOnMainThread(entity, () -> {
                        try {
                            if (isCurrent(entityId, state) && isEntityValid(entity)) onRunning(entity);
                        } catch (Exception e) {
                            ModLogger.getLogger().error("Skill task error on main thread", e);
                            cancelState(entityId, state, CancellationReason.ERROR, true);
                        }
                    });
                } else {
                    onRunning(entity);
                }
            } else {
                cancelState(entityId, state, CancellationReason.ENTITY_INVALID, true);
            }
        } catch (Exception e) {
            ModLogger.getLogger().error("Skill task error", e);
            cancelState(entityId, state, CancellationReason.ERROR, true);
        }
    }

    private void stopTimerSafely(UUID entityId, TimerState<T> state) {
        T entity = state.entity;
        Runnable stopTask = () -> {
            if (!isCurrent(entityId, state)) return;
            if (!isEntityValid(entity)) {
                cancelState(entityId, state, CancellationReason.ENTITY_INVALID, true);
                return;
            }
            // Release ownership before invoking user code so onEnd may safely
            // start a replacement timer of the same class for the entity.
            if (!removeState(entityId, state)) return;
            try {
                onEnd(entity);
            } catch (Exception e) {
                ModLogger.getLogger().error("Skill timer end callback failed", e);
            }
        };
        if (requireMainThread && entity != null) {
            ThreadExecutor.executeOnMainThread(entity, stopTask);
        } else {
            stopTask.run();
        }
    }

    private boolean isEntityValid(@Nullable T entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private boolean isCurrent(UUID entityId, TimerState<T> state) {
        return timerMap.get(entityId) == state;
    }

    public void removeTimer(UUID livingId) {
        cancelTimer(livingId, CancellationReason.EXPLICIT, true);
    }

    private void cancelTimer(UUID livingId, CancellationReason reason, boolean notify) {
        TimerState<T> state = timerMap.get(livingId);
        if (state != null) cancelState(livingId, state, reason, notify);
    }

    private void cancelState(UUID livingId, TimerState<T> state, CancellationReason reason, boolean notify) {
        if (!removeState(livingId, state)) return;
        if (notify) {
            Runnable callback = () -> {
                try {
                    onCancelled(state.entity, reason);
                } catch (Exception e) {
                    ModLogger.getLogger().error("Skill timer cancellation callback failed", e);
                }
            };
            if (requireMainThread) ThreadExecutor.executeOnMainThread(state.entity, callback);
            else callback.run();
        }
    }

    private boolean removeState(UUID livingId, TimerState<T> state) {
        if (!timerMap.remove(livingId, state)) return false;
        state.cancelFutures();
        Map<UUID, TimerEntry<?>> entryMap = globalEntityEntryMap.get(this.getClass());
        if (entryMap != null && entryMap.remove(livingId, this)) {
            if (entryMap.isEmpty()) globalEntityEntryMap.remove(this.getClass(), entryMap);
        }
        Map<UUID, Boolean> locks = GLOBAL_CLASS_ENTITY_LOCK.get(this.getClass());
        if (locks != null) {
            locks.remove(livingId);
            if (locks.isEmpty()) GLOBAL_CLASS_ENTITY_LOCK.remove(this.getClass(), locks);
        }
        return true;
    }

    public static void shutdown(LivingEntity living) {
        if (living == null) {
            ModLogger.getLogger().warn("shutdown fail! living is null");
            return;
        }
        UUID uuid = living.getUUID();
        for (Map<UUID, TimerEntry<?>> timers : globalEntityEntryMap.values()) {
            TimerEntry<?> timer = timers.get(uuid);
            if (timer != null) {
                timer.cancelTimer(uuid, CancellationReason.LOGOUT, true);
            }
        }
    }

    public static void shutdownAll() {
        synchronized (TimerEntry.class) {
            // Cancel both futures before shutting down the executor. Otherwise
            // long-delay end tasks can keep shutdown waiting for no useful work.
            for (Map<UUID, TimerEntry<?>> timers : globalEntityEntryMap.values()) {
                for (TimerEntry<?> timer : timers.values()) {
                    for (UUID uuid : new ArrayList<>(timer.timerMap.keySet())) {
                        timer.cancelTimer(uuid, CancellationReason.EXPLICIT, false);
                    }
                }
            }
            globalEntityEntryMap.clear();
            GLOBAL_CLASS_ENTITY_LOCK.clear();
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
        Map<UUID, TimerEntry<?>> map = globalEntityEntryMap.get(entryClass);
        return map != null && map.containsKey(uuid);
    }

    private String getTimerIdentifier() {
        Class<?> clazz = this.getClass();
        return clazz.isAnonymousClass() ?
                "Anonymous@" + Integer.toHexString(clazz.hashCode()) :
                clazz.getSimpleName();
    }

    public void onStart(@NotNull T entity) {}
    public void onRunning(@NotNull T entity) {}
    public void onEnd(@NotNull T entity) {}
    public void onCancelled(@NotNull T entity, @NotNull CancellationReason reason) {}
}
