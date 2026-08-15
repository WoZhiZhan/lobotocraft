package com.wzz.lobotocraft.entity.base;

public final class QliphothCounterPolicy {
    private QliphothCounterPolicy() {
    }

    public static Result decrease(int current, int amount) {
        int normalized = Math.max(0, current);
        if (amount <= 0 || normalized == 0) return new Result(normalized, false);
        int next = Math.max(0, normalized - amount);
        return new Result(next, next == 0);
    }

    public record Result(int value, boolean firstMeltdown) {
    }
}
