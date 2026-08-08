package com.wzz.lobotocraft.ego;

public enum EgoResearchScope {
    PERSONAL("personal"),
    AI_GLOBAL("ai_global");

    private final String id;

    EgoResearchScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static EgoResearchScope byId(String id) {
        for (EgoResearchScope value : values()) {
            if (value.id.equals(id)) return value;
        }
        return PERSONAL;
    }
}
