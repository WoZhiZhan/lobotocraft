package com.wzz.lobotocraft.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

public final class MobAiLockUtil {
    private static final String ROOT_TAG = "lobotocraft_ai_locks";
    private static final String ORIGINAL_TAG = "lobotocraft_ai_lock_original_no_ai";

    private MobAiLockUtil() {
    }

    public static void acquire(Mob mob, String source) {
        if (mob == null || source == null || source.isBlank()) return;
        CompoundTag data = mob.getPersistentData();
        CompoundTag locks = data.getCompound(ROOT_TAG);
        if (locks.isEmpty()) data.putBoolean(ORIGINAL_TAG, mob.isNoAi());
        locks.putBoolean(source, true);
        data.put(ROOT_TAG, locks);
        mob.setNoAi(true);
    }

    public static void release(Mob mob, String source) {
        if (mob == null || source == null || source.isBlank()) return;
        CompoundTag data = mob.getPersistentData();
        CompoundTag locks = data.getCompound(ROOT_TAG);
        locks.remove(source);
        if (!locks.isEmpty()) {
            data.put(ROOT_TAG, locks);
            mob.setNoAi(true);
            return;
        }
        boolean original = data.getBoolean(ORIGINAL_TAG);
        data.remove(ROOT_TAG);
        data.remove(ORIGINAL_TAG);
        mob.setNoAi(original);
    }
}
