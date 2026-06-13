package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 工作进行中每tick触发
 */
public class WorkTickEvent extends Event {
    private final ServerPlayer player;
    private final IAbnormality abnormality;
    private final WorkType workType;
    private final int currentTick;
    private final int extractionInterval;
    private final WorkManager.WorkSession session;
    private final int workDuration;

    public WorkTickEvent(ServerPlayer player, IAbnormality abnormality,
                         WorkType workType, int currentTick,
                         int extractionInterval, WorkManager.WorkSession session) {
        this.player = player;
        this.abnormality = abnormality;
        this.workType = workType;
        this.currentTick = currentTick;
        this.extractionInterval = extractionInterval;
        this.session = session;
        this.workDuration = session != null ? session.workDuration : 0;
    }

    public ServerPlayer getPlayer() { return player; }
    public IAbnormality getAbnormality() { return abnormality; }
    public WorkType getWorkType() { return workType; }
    public int getCurrentTick() { return currentTick; }
    public int getExtractionInterval() { return extractionInterval; }
    public WorkManager.WorkSession getSession() { return session; }
    public float getProgress() { 
        return (float) currentTick / extractionInterval; 
    }
    public int getWorkDuration() { return workDuration; }
    public int getWorkDurationSeconds() { return workDuration / 20; }
}