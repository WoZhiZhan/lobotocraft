package com.wzz.lobotocraft.event.definition.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkManager.WorkSession;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 工作中断时触发
 */
public class WorkInterruptEvent extends PlayerEvent {
    private final IAbnormality abnormality;
    private final WorkType workType;
    private final String reason;
    private final WorkSession session;

    public WorkInterruptEvent(ServerPlayer player, IAbnormality abnormality,
                             WorkType workType, String reason, WorkSession session) {
        super(player);
        this.abnormality = abnormality;
        this.workType = workType;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public WorkSession getSession() {
        return session;
    }

    public int getCurrentExtractionCount() {
        return session.extractionCount;
    }
}