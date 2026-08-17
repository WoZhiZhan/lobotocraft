package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GeburahCounterPassive {
    public static final int RESET_TICKS = 10 * 20;
    private static final int TRIGGERS_PER_COUNTER = 3;

    private final Map<UUID, Entry> entries = new HashMap<>();

    public Result record(UUID responsibleId, long gameTime) {
        if (responsibleId == null) return Result.IMMUNE;
        Entry previous = entries.get(responsibleId);
        int count = previous == null || gameTime - previous.lastTriggerTick() >= RESET_TICKS
                ? 1 : previous.count() + 1;
        if (count >= TRIGGERS_PER_COUNTER) {
            entries.remove(responsibleId);
            return Result.COUNTER;
        }
        entries.put(responsibleId, new Entry(count, gameTime));
        return Result.IMMUNE;
    }

    public int count(UUID responsibleId, long gameTime) {
        Entry entry = entries.get(responsibleId);
        if (entry == null) return 0;
        if (gameTime - entry.lastTriggerTick() >= RESET_TICKS) {
            entries.remove(responsibleId);
            return 0;
        }
        return entry.count();
    }

    public void clear() {
        entries.clear();
    }

    public enum Result {
        IMMUNE,
        COUNTER
    }

    private record Entry(int count, long lastTriggerTick) {
    }
}
