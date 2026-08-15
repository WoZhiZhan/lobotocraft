package com.wzz.lobotocraft.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class MovementLockUtil {
    private static final String ROOT_TAG = "lobotocraft_movement_locks";
    private static final String LEGACY_TAG = "NotMove";

    private MovementLockUtil() {
    }

    public static void acquire(LivingEntity entity, String source) {
        if (entity == null || source == null || source.isBlank()) return;
        CompoundTag data = entity.getPersistentData();
        CompoundTag locks = data.getCompound(ROOT_TAG);
        locks.putBoolean(source, true);
        data.put(ROOT_TAG, locks);
        data.putBoolean(LEGACY_TAG, true);
    }

    public static void release(LivingEntity entity, String source) {
        if (entity == null || source == null || source.isBlank()) return;
        CompoundTag data = entity.getPersistentData();
        CompoundTag locks = data.getCompound(ROOT_TAG);
        locks.remove(source);
        if (locks.isEmpty()) {
            data.remove(ROOT_TAG);
            data.remove(LEGACY_TAG);
        } else {
            data.put(ROOT_TAG, locks);
            data.putBoolean(LEGACY_TAG, true);
        }
    }

    public static void clearAll(LivingEntity entity) {
        if (entity == null) return;
        entity.getPersistentData().remove(ROOT_TAG);
        entity.getPersistentData().remove(LEGACY_TAG);
    }

    public static boolean isLocked(LivingEntity entity) {
        if (entity == null) return false;
        CompoundTag data = entity.getPersistentData();
        return data.getBoolean(LEGACY_TAG) || !data.getCompound(ROOT_TAG).isEmpty();
    }
}
