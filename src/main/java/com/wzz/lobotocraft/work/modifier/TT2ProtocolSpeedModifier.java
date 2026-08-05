package com.wzz.lobotocraft.work.modifier;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.work.IWorkSpeedModifier;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.server.level.ServerPlayer;

public class TT2ProtocolSpeedModifier implements IWorkSpeedModifier {
    
    private static TT2ProtocolSpeedModifier instance;
    
    public static TT2ProtocolSpeedModifier getInstance() {
        if (instance == null) {
            instance = new TT2ProtocolSpeedModifier();
        }
        return instance;
    }
    
    @Override
    public float getSpeedMultiplier(ServerPlayer player, WorkManager.WorkSession session) {
        if (isEnabled(player)) {
            return 2.0f;
        }
        return 1.0f;
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
    
    @Override
    public boolean isActive(ServerPlayer player, WorkManager.WorkSession session) {
        return isEnabled(player);
    }

    private static boolean isEnabled(ServerPlayer player) {
        return player != null && FacilityDayData.get(player.server.overworld()).isTt2ProtocolEnabled();
    }
}
