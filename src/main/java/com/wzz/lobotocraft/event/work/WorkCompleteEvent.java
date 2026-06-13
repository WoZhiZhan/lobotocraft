package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager.WorkSession;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 工作完成时触发
 */
public class WorkCompleteEvent extends PlayerEvent {
    private final IAbnormality abnormality;
    private final WorkType workType;
    private final WorkResult result;
    private final int peOutput;
    private final WorkSession session;
    private final boolean forcedEnd;

    public WorkCompleteEvent(ServerPlayer player, IAbnormality abnormality,
                            WorkType workType, WorkResult result, 
                            int peOutput, WorkSession session, boolean forcedEnd) {
        super(player);
        this.abnormality = abnormality;
        this.workType = workType;
        this.result = result;
        this.peOutput = peOutput;
        this.session = session;
        this.forcedEnd = forcedEnd;
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

    public WorkResult getWorkResult() {
        return result;
    }

    public int getPeOutput() {
        return peOutput;
    }

    public WorkSession getSession() {
        return session;
    }

    public boolean isForcedEnd() {
        return forcedEnd;
    }

    public int getSuccessCount() {
        return session.successCount;
    }

    public int getFailureCount() {
        return session.failureCount;
    }

    public float getSuccessRate() {
        int total = session.successCount + session.failureCount;
        return total > 0 ? (float) session.successCount / total : 0f;
    }
}