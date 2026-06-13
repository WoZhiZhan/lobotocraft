package com.wzz.lobotocraft.entity.data;

public enum RiskLevel {
    ZAYIN(0xFF00FF00),    // 绿色
    TETH(0xFF00AAFF),     // 蓝色
    HE(0xFFFFFF00),         // 黄色
    WAW(0xFFFF00FF),       // 紫色
    ALEPH(0xFFFF0000);   // 红色

    private final int color;
    
    RiskLevel(int color) {
        this.color = color;
    }

    public int getColor() { return color; }
}