package com.wzz.lobotocraft.event.definition.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * 异想体停止出逃事件 - 在异想体停止出逃时触发
 * 此事件不可取消，仅用于通知其他系统
 */
public class AbnormalityEscapeStopEvent extends Event {
    
    private final AbstractAbnormality abnormality;
    private final boolean wasKilled; // 是否因为被击杀而停止出逃
    private final UUID suppressionAttacker;
    
    public AbnormalityEscapeStopEvent(AbstractAbnormality abnormality, boolean wasKilled) {
        this(abnormality, wasKilled, null);
    }

    public AbnormalityEscapeStopEvent(AbstractAbnormality abnormality, boolean wasKilled,
                                     @Nullable UUID suppressionAttacker) {
        this.abnormality = abnormality;
        this.wasKilled = wasKilled;
        this.suppressionAttacker = wasKilled ? suppressionAttacker : null;
    }

    /** Responsibility captured at the lethal hit; not the mob's later targeting memory. */
    @Nullable
    public UUID getSuppressionAttackerUUID() {
        return suppressionAttacker;
    }
    
    /**
     * 获取停止出逃的异想体
     */
    public AbstractAbnormality getAbnormality() {
        return abnormality;
    }
    
    /**
     * 获取异想体编号
     */
    public String getAbnormalityCode() {
        return abnormality.getAbnormalityCode();
    }
    
    /**
     * 获取异想体名称
     */
    public String getAbnormalityName() {
        return abnormality.getAbnormalityName();
    }
    
    /**
     * 是否因为被击杀而停止出逃
     */
    public boolean wasKilled() {
        return wasKilled;
    }
    
    /**
     * 是否正常镇压（非击杀）
     */
    public boolean wasSuppressed() {
        return !wasKilled;
    }
}
