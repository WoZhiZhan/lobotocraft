package com.wzz.lobotocraft.work;

/**
 * 工作结果枚举
 */
public enum WorkResult {
    GOOD("优", 0x00FF00),      // 绿色
    NORMAL("良", 0xFFFF00),    // 黄色
    BAD("差", 0xFF0000);       // 红色
    
    private final String displayName;
    private final int color;
    
    WorkResult(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getColor() {
        return color;
    }
}
