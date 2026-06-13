package com.wzz.lobotocraft.integration.jade;

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

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof AbstractAbnormality abnormality) {
            // 同步逆卡巴拉计数器
            data.putInt("QliphothCounter", abnormality.getQliphothCounter());
            data.putInt("MaxQliphothCounter", abnormality.getMaxQliphothCounter());
            
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

    @Override
    public ResourceLocation getUid() {
        return LobotocraftJadePlugin.QLIPHOTH_COUNTER;
    }
}