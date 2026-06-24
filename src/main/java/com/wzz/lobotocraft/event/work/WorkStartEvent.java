package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 工作开始前触发，可以取消
 */
@Cancelable
public class WorkStartEvent extends PlayerEvent {
    private final IAbnormality abnormality;
    private final WorkType workType;
    private String cancelReason = "工作被取消";

    public WorkStartEvent(ServerPlayer player, IAbnormality abnormality, WorkType workType) {
        super(player);
        this.abnormality = abnormality;
        this.workType = workType;
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

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String reason) {
        this.cancelReason = reason;
    }
}
