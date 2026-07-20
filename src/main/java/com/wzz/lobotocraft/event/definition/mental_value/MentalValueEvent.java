package com.wzz.lobotocraft.event.definition.mental_value;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 精神值事件基类
 */
public class MentalValueEvent extends PlayerEvent {
    private final float originalValue;
    private final float maxMentalValue;

    public MentalValueEvent(Player player, float originalValue, float maxMentalValue) {
        super(player);
        this.originalValue = originalValue;
        this.maxMentalValue = maxMentalValue;
    }

    public float getOriginalValue() {
        return originalValue;
    }

    public float getMaxMentalValue() {
        return maxMentalValue;
    }

    /**
     * 精神值变更前事件（可取消）
     */
    @Cancelable
    public static class Pre extends MentalValueEvent {
        private float newValue;
        private final ChangeType changeType;
        private final Object source;

        public Pre(Player player, float originalValue, float maxMentalValue, float newValue, ChangeType changeType, Object source) {
            super(player, originalValue, maxMentalValue);
            this.newValue = newValue;
            this.changeType = changeType;
            this.source = source;
        }

        public float getNewValue() {
            return newValue;
        }

        public void setNewValue(float newValue) {
            this.newValue = Math.max(0, newValue);
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public Object getSource() {
            return source;
        }
    }

    /**
     * 精神值变更后事件
     */
    public static class Post extends MentalValueEvent {
        private final float newValue;
        private final ChangeType changeType;
        private final Object source;

        public Post(Player player, float originalValue, float maxMentalValue, float newValue, ChangeType changeType, Object source) {
            super(player, originalValue, maxMentalValue);
            this.newValue = newValue;
            this.changeType = changeType;
            this.source = source;
        }

        public float getNewValue() {
            return newValue;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public Object getSource() {
            return source;
        }
    }

    /**
     * 最大精神值变更事件
     */
    public static class MaxChanged extends MentalValueEvent {
        private final float oldMaxValue;
        private final float newMaxValue;

        public MaxChanged(Player player, float originalValue, float oldMaxValue, float newMaxValue) {
            super(player, originalValue, newMaxValue);
            this.oldMaxValue = oldMaxValue;
            this.newMaxValue = newMaxValue;
        }

        public float getOldMaxValue() {
            return oldMaxValue;
        }

        public float getNewMaxValue() {
            return newMaxValue;
        }
    }

    /**
     * 精神值耗尽事件
     */
    public static class Depleted extends MentalValueEvent {
        private final Object cause;

        public Depleted(Player player, float maxMentalValue, Object cause) {
            super(player, 0, maxMentalValue);
            this.cause = cause;
        }

        public Object getCause() {
            return cause;
        }
    }

    /**
     * 变化类型枚举
     */
    public enum ChangeType {
        SET,        // 直接设置
        ADD,        // 增加
        REDUCE,     // 减少
        REGENERATE, // 自然恢复
        DAMAGE,     // 伤害
        SYNC,       // 同步
        OTHER       // 其他
    }
}