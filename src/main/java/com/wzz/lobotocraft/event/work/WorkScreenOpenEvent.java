package com.wzz.lobotocraft.event.work;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 工作屏幕打开事件
 * 在玩家尝试打开异想体工作屏幕时触发，可以取消
 */
@Cancelable
public class WorkScreenOpenEvent extends PlayerEvent {
    private final IAbnormality abnormality;
    private final String abnormalityCode;
    private final String abnormalityName;
    private final boolean isShiftClick;
    
    public WorkScreenOpenEvent(Player player, IAbnormality abnormality, boolean isShiftClick) {
        super(player);
        this.abnormality = abnormality;
        this.abnormalityCode = abnormality.getAbnormalityCode();
        this.abnormalityName = abnormality.getAbnormalityName();
        this.isShiftClick = isShiftClick;
    }
    
    /**
     * 获取关联的异想体
     */
    public IAbnormality getAbnormality() {
        return abnormality;
    }
    
    /**
     * 获取异想体代码
     */
    public String getAbnormalityCode() {
        return abnormalityCode;
    }
    
    /**
     * 获取异想体名称
     */
    public String getAbnormalityName() {
        return abnormalityName;
    }
    
    /**
     * 是否按住Shift键
     */
    public boolean isShiftClick() {
        return isShiftClick;
    }
    
    /**
     * 是否正在打开工作屏幕（普通右键）
     */
    public boolean isOpeningWorkScreen() {
        return !isShiftClick;
    }
    
    /**
     * 是否正在打开图鉴屏幕（Shift+右键）
     */
    public boolean isOpeningManualScreen() {
        return isShiftClick;
    }
}