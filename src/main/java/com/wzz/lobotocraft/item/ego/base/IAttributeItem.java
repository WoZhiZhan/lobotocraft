package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.item.AttributeEntry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public interface IAttributeItem {
    
    /**
     * 是否有属性加成
     */
    boolean hasAttribute();

    /**
     * 获取所有属性条目（新方法 - 支持多属性）
     * @return 属性条目列表
     */
    default List<AttributeEntry> getAttributeEntries() {
        return getAttributeEntries(null);
    }

    default List<AttributeEntry> getAttributeEntries(@Nullable LivingEntity living) {
        // 默认实现：使用旧的单属性方法构建单个条目
        if (!hasAttribute() || getAttribute() == null || getAttributeUUID() == null) {
            return Collections.emptyList();
        }

        AttributeEntry entry = new AttributeEntry(
                getAttributeUUID(),
                getAttributeName(),
                getAttribute(),
                getAttributeBonus(),
                getAttributeMode()
        );
        return Collections.singletonList(entry);
    }

    /**
     * @deprecated 使用 getAttributeEntries() 代替
     */
    @Deprecated
    default UUID getAttributeUUID() {
        return null;
    }

    /**
     * @deprecated 使用 getAttributeEntries() 代替
     */
    @Deprecated
    default float getAttributeBonus() {
        return 0;
    }

    /**
     * @deprecated 使用 getAttributeEntries() 代替
     */
    @Deprecated
    default String getAttributeName() {
        return "Attribute";
    }

    /**
     * @deprecated 使用 getAttributeEntries() 代替
     */
    @Deprecated
    default Mode getAttributeMode() {
        return Mode.ADDITION;
    }

    /**
     * @deprecated 使用 getAttributeEntries() 代替
     */
    @Deprecated
    default Attribute getAttribute() {
        return null;
    }

    enum Mode {
        ADDITION, MULTIPLY_BASE
    }
}