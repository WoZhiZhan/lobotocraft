package com.wzz.lobotocraft.item.ego.base;

import java.util.EnumSet;
import java.util.Set;

public interface IAnimatablePerspective {

    /**
     * 返回允许播放动画的视角
     * 不涉及服务端类，使用自定义枚举代替客户端 ItemDisplayContext
     */
    default Set<Perspective> getAllowedPerspectives() {
        return EnumSet.allOf(Perspective.class);
    }

    /**
     * 自定义视角枚举
     */
    enum Perspective {
        FIRST_PERSON_RIGHT,
        FIRST_PERSON_LEFT,
        THIRD_PERSON_RIGHT,
        THIRD_PERSON_LEFT,
        GUI,
        GROUND,
        FIXED,
        HEAD
    }

    default boolean shouldPlayInPerspective(Perspective perspective) {
        return getAllowedPerspectives().contains(perspective);
    }
}
