package com.wzz.lobotocraft.work;

/**
 * 工作类型枚举
 */
public enum WorkType {
    INSTINCT("instinct", "本能"),
    INSIGHT("insight", "洞察"),
    ATTACHMENT("attachment", "沟通"),
    REPRESSION("repression", "压迫");
    
    private final String name;
    private final String displayName;
    
    WorkType(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

