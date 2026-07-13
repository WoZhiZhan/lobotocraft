package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.item.ego.base.IAttributeItem;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * 属性条目，存储单个属性的完整信息
 */
public class AttributeEntry {
    private final UUID uuid;
    private final String name;
    private final Attribute attribute;
    private final double value;
    private final AttributeModifier.Operation operation;

    public AttributeEntry(UUID uuid, String name, Attribute attribute, double value, AttributeModifier.Operation operation) {
        this.uuid = uuid;
        this.name = name;
        this.attribute = attribute;
        this.value = value;
        this.operation = operation;
    }

    /**
     * 便捷构造方法 - 使用加法模式
     */
    public AttributeEntry(UUID uuid, String name, Attribute attribute, double value) {
        this(uuid, name, attribute, value, AttributeModifier.Operation.ADDITION);
    }

    /**
     * 便捷构造方法 - 使用旧的Mode枚举
     */
    public AttributeEntry(UUID uuid, String name, Attribute attribute, double value, IAttributeItem.Mode mode) {
        this(uuid, name, attribute, value, getOperation(mode));
    }

    private static AttributeModifier.Operation getOperation(IAttributeItem.Mode mode) {
        return switch (mode) {
            case ADDITION -> AttributeModifier.Operation.ADDITION;
            case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
        };
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public double getValue() {
        return value;
    }

    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    /**
     * 创建属性修改器
     */
    public AttributeModifier createModifier() {
        return new AttributeModifier(uuid, name, value, operation);
    }

    /**
     * Builder模式创建属性条目
     */
    public static class Builder {
        private UUID uuid;
        private String name;
        private Attribute attribute;
        private double value;
        private AttributeModifier.Operation operation = AttributeModifier.Operation.ADDITION;

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder attribute(Attribute attribute) {
            this.attribute = attribute;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public Builder operation(AttributeModifier.Operation operation) {
            this.operation = operation;
            return this;
        }

        public Builder mode(IAttributeItem.Mode mode) {
            this.operation = mode == IAttributeItem.Mode.MULTIPLY_BASE 
                ? AttributeModifier.Operation.MULTIPLY_BASE 
                : AttributeModifier.Operation.ADDITION;
            return this;
        }

        public AttributeEntry build() {
            if (uuid == null || name == null || attribute == null) {
                throw new IllegalStateException("UUID, name and attribute must be set");
            }
            return new AttributeEntry(uuid, name, attribute, value, operation);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}