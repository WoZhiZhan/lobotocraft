package com.wzz.lobotocraft.integration.jade;

import com.wzz.lobotocraft.entity.abnormality.EntityCrumblingArmor;
import com.wzz.lobotocraft.entity.abnormality.EntityHappyTeddy;
import com.wzz.lobotocraft.entity.abnormality.EntityLeticia;
import com.wzz.lobotocraft.entity.abnormality.EntityOneBad;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.abnormality.EntityWingBeat;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade服务端数据提供器
 * 将异想体的计数器信息同步到客户端
 */
public enum AbnormalityDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final int NO_COUNTER_DISPLAY_VALUE = 114514;

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof AbstractAbnormality abnormality) {
            // 同步逆卡巴拉计数器
            if (shouldDisplayNoCounter(abnormality)) {
                data.putInt("QliphothCounter", NO_COUNTER_DISPLAY_VALUE);
                data.putInt("MaxQliphothCounter", NO_COUNTER_DISPLAY_VALUE);
            } else {
                data.putInt("QliphothCounter", abnormality.getQliphothCounter());
                data.putInt("MaxQliphothCounter", abnormality.getMaxQliphothCounter());
            }
            
            // 同步异想体基本信息
            data.putString("AbnormalityCode", abnormality.getAbnormalityCode());
            data.putString("AbnormalityName", abnormality.getAbnormalityName());
            data.putString("AbnormalityJadeName", abnormality.getAbnormalityJadeName());
            data.putString("RiskLevel", abnormality.getRiskLevel().toString());
            data.putInt("RiskLevelColor", abnormality.getRiskLevel().getColor());

            // 同步是否可以出逃
            data.putBoolean("CanEscape", abnormality.canEscape());

            if (abnormality.hasEscape()) {
                data.putBoolean("HasEscaped", abnormality.hasEscape());
            }
        }
    }

    private boolean shouldDisplayNoCounter(AbstractAbnormality abnormality) {
        return abnormality instanceof EntityOneBad
                || abnormality instanceof EntityHappyTeddy
                || abnormality instanceof EntityWingBeat
                || abnormality instanceof EntitySnowQueen
                || abnormality instanceof EntityCrumblingArmor
                || abnormality instanceof EntityLeticia;
    }

    @Override
    public ResourceLocation getUid() {
        return LobotocraftJadePlugin.QLIPHOTH_COUNTER;
    }
}
