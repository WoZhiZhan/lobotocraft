package com.wzz.lobotocraft.core_suppression.geburah;

public enum GeburahActionWindow {
    WINDUP(1.0F),
    ACTIVE(0.15F),
    RECOVERY(1.75F),
    OTHER(0.0F);

    private final float postureMultiplier;

    GeburahActionWindow(float postureMultiplier) {
        this.postureMultiplier = postureMultiplier;
    }

    public float postureMultiplier() {
        return postureMultiplier;
    }
}
