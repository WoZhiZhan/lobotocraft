package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 工作失败即将造成伤害时触发（可取消）
 */
@Cancelable
public class WorkDamageEvent extends Event {
    private final ServerPlayer player;
    private final AbstractAbnormality abnormality;
    private final WorkType workType;
    private final int extractionCount;
    private final WorkManager.WorkSession session;
    private String cancelMessage = "";

    public WorkDamageEvent(ServerPlayer player, AbstractAbnormality abnormality,
                          WorkType workType, int extractionCount,
                          WorkManager.WorkSession session) {
        this.player = player;
        this.abnormality = abnormality;
        this.workType = workType;
        this.extractionCount = extractionCount;
        this.session = session;
    }

    public ServerPlayer getPlayer() { return player; }
    public AbstractAbnormality getAbnormality() { return abnormality; }
    public WorkType getWorkType() { return workType; }
    public int getExtractionCount() { return extractionCount; }
    public WorkManager.WorkSession getSession() { return session; }
    
    public String getCancelMessage() { return cancelMessage; }
    public void setCancelMessage(String message) { this.cancelMessage = message; }
}