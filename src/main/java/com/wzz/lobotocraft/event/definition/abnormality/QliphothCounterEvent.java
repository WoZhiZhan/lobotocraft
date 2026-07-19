package com.wzz.lobotocraft.event.definition.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraftforge.eventbus.api.Event;

/**
 * 逆卡巴拉计数器变化事件
 */
public class QliphothCounterEvent extends Event {
    
    private final AbstractAbnormality abnormality;
    private final int oldValue;
    private final int newValue;
    private final boolean isMeltdown; // 是否触发融毁
    
    public QliphothCounterEvent(AbstractAbnormality abnormality, int oldValue, int newValue, boolean isMeltdown) {
        this.abnormality = abnormality;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.isMeltdown = isMeltdown;
    }
    
    public AbstractAbnormality getAbnormality() { return abnormality; }
    public int getOldValue() { return oldValue; }
    public int getNewValue() { return newValue; }
    public boolean isMeltdown() { return isMeltdown; }
}