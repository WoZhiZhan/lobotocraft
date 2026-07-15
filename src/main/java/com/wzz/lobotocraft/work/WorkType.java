package com.wzz.lobotocraft.work;

/**
 * 工作类型枚举
 */
public enum WorkType {
    INSTINCT("instinct", "本能", "white"),
    INSIGHT("insight", "洞察", "red"),
    ATTACHMENT("attachment", "沟通", "black"),
    REPRESSION("repression", "压迫", "blue");
    
    private final String name;
    private final String displayName;
    private final String damageType;

    WorkType(String name, String displayName, String damageType) {
        this.name = name;
        this.displayName = displayName;
        this.damageType = damageType;
    }
    
    public String getName() {
        return name;
    }

    public String getDamageType() {
        return damageType;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

