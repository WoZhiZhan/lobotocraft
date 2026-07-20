package com.wzz.lobotocraft.capability;

import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public interface IMentalValue {
    float getMentalValue();

    void setMentalValue(float value);

    void setMentalValue(float value, MentalValueEvent.ChangeType changeType);

    float getMaxMentalValue();

    void setMaxMentalValue(float value);

    void addMentalValue(float amount);

    void addMentalValue(float amount, MentalValueEvent.ChangeType changeType);

    void reduceMentalValue(float amount);

    void reduceMentalValue(float amount, MentalValueEvent.ChangeType changeType);

    boolean isMentalValueEmpty();

    void copyFrom(IMentalValue source);

    void saveNBTData(CompoundTag nbt);

    void loadNBTData(CompoundTag nbt);
    /**
     * 获取额外精神值（来自装备、饰品等）
     */
    float getExtraMentalValue();

    /**
     * 设置额外精神值
     */
    void setExtraMentalValue(float value);

    /**
     * 获取有效最大精神值（基础最大值 + 额外精神值）
     */
    float getEffectiveMaxMentalValue();
}