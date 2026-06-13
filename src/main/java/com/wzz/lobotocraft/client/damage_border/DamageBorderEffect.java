package com.wzz.lobotocraft.client.damage_border;

/**
 * 伤害边框效果数据
 * 记录伤害类型和开始时间，用于淡入淡出动画
 */
public class DamageBorderEffect {
    private final DamageType damageType;
    private final long startTime;
    private final int duration; // 持续时间（毫秒）
    
    public enum DamageType {
        RED("red"),
        WHITE("white"),
        BLACK("black"),
        BLUE("blue");
        
        private final String id;
        
        DamageType(String id) {
            this.id = id;
        }
        
        public String getId() {
            return id;
        }
        
        public static DamageType fromString(String id) {
            for (DamageType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    public DamageBorderEffect(DamageType damageType, int durationMs) {
        this.damageType = damageType;
        this.startTime = System.currentTimeMillis();
        this.duration = durationMs;
    }
    
    /**
     * 获取当前透明度（0-255）
     * 淡入淡出效果：0 -> 255 -> 0
     */
    public int getAlpha() {
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (elapsed >= duration) {
            return 0; // 动画结束
        }
        
        // 计算淡入淡出进度（0.0 - 1.0）
        float progress = (float) elapsed / duration;
        
        // 使用sin函数实现平滑的淡入淡出
        // sin(progress * PI) 会从 0 -> 1 -> 0
        float alpha = (float) Math.sin(progress * Math.PI);
        
        return (int) (alpha * 255);
    }
    
    /**
     * 检查效果是否已结束
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - startTime >= duration;
    }
    
    public DamageType getDamageType() {
        return damageType;
    }
}