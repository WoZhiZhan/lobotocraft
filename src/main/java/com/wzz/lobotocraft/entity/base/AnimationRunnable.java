package com.wzz.lobotocraft.entity.base;

@FunctionalInterface
public interface AnimationRunnable {
    boolean run();

    default String newAnimation() {
        return null;
    }
}
