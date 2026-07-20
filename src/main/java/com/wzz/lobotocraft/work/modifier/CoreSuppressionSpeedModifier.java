package com.wzz.lobotocraft.work.modifier;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.work.IWorkSpeedModifier;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.server.level.ServerPlayer;

public final class CoreSuppressionSpeedModifier implements IWorkSpeedModifier {
    private static final CoreSuppressionSpeedModifier INSTANCE = new CoreSuppressionSpeedModifier();

    private CoreSuppressionSpeedModifier() {
    }

    public static CoreSuppressionSpeedModifier getInstance() {
        return INSTANCE;
    }

    @Override
    public float getSpeedMultiplier(ServerPlayer player, WorkManager.WorkSession session) {
        return CoreSuppressionManager.getWorkSpeedMultiplier(player);
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
