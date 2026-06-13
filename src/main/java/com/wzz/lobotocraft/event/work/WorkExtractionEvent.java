package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager.WorkSession;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 每次工作提取时触发
 */
public class WorkExtractionEvent extends PlayerEvent {
    private final IAbnormality abnormality;
    private final WorkType workType;
    private final boolean success;
    private final int extractionCount;
    private final WorkSession session;

    public WorkExtractionEvent(ServerPlayer player, IAbnormality abnormality, 
                               WorkType workType, boolean success, 
                               int extractionCount, WorkSession session) {
        super(player);
        this.abnormality = abnormality;
        this.workType = workType;
        this.success = success;
        this.extractionCount = extractionCount;
        this.session = session;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    public IAbnormality getAbnormality() {
        return abnormality;
    }

    public WorkType getWorkType() {
        return workType;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getExtractionCount() {
        return extractionCount;
    }

    public WorkSession getSession() {
        return session;
    }

    public float getProgress() {
        return session.getProgress();
    }
}