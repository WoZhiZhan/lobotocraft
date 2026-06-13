package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 提取判定前触发（可取消，可修改成功率）
 */
@Cancelable
public class WorkPreExtractionEvent extends Event {
    private final ServerPlayer player;
    private final IAbnormality abnormality;
    private final WorkType workType;
    private final int extractionCount;
    private final WorkManager.WorkSession session;
    private float successRate;
    private String cancelReason = "";

    public WorkPreExtractionEvent(ServerPlayer player, IAbnormality abnormality,
                                 WorkType workType, int extractionCount,
                                 float successRate, WorkManager.WorkSession session) {
        this.player = player;
        this.abnormality = abnormality;
        this.workType = workType;
        this.extractionCount = extractionCount;
        this.successRate = successRate;
        this.session = session;
    }

    public ServerPlayer getPlayer() { return player; }
    public IAbnormality getAbnormality() { return abnormality; }
    public WorkType getWorkType() { return workType; }
    public int getExtractionCount() { return extractionCount; }
    public WorkManager.WorkSession getSession() { return session; }
    
    public float getSuccessRate() { return successRate; }
    public void setSuccessRate(float rate) { 
        this.successRate = Math.max(0.0f, Math.min(1.0f, rate)); 
    }
    
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }
}