package com.wzz.lobotocraft.work.modifier;

import com.wzz.lobotocraft.work.IWorkSpeedModifier;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.server.level.ServerPlayer;

public class ContinuousWorkSpeedModifier implements IWorkSpeedModifier {

    private static ContinuousWorkSpeedModifier instance;

    public static ContinuousWorkSpeedModifier getInstance() {
        if (instance == null) {
            instance = new ContinuousWorkSpeedModifier();
        }
        return instance;
    }

    @Override
    public float getSpeedMultiplier(ServerPlayer player, WorkManager.WorkSession session) {
        return 5.0f;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public boolean isActive(ServerPlayer player, WorkManager.WorkSession session) {
        return session != null && session.continuousMode;
    }
}
