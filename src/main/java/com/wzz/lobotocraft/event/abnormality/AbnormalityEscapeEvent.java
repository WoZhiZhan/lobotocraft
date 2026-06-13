package com.wzz.lobotocraft.event.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraftforge.eventbus.api.Event;

/**
 * 异想体出逃事件 - 在异想体开始出逃时触发
 * 此事件不可取消，仅用于通知其他系统
 */
public class AbnormalityEscapeEvent extends Event {
    
    private final AbstractAbnormality abnormality;
    
    public AbnormalityEscapeEvent(AbstractAbnormality abnormality) {
        this.abnormality = abnormality;
    }
    
    /**
     * 获取出逃的异想体
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
     * 获取出逃位置
     */
    public net.minecraft.core.BlockPos getEscapePosition() {
        return abnormality.escapePosition;
    }
}