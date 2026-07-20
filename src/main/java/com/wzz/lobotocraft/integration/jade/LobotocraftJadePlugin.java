package com.wzz.lobotocraft.integration.jade;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.Identifiers;

/**
 * Jade模组联动插件
 * 显示异想体的逆卡巴拉计数器信息
 */
@WailaPlugin
public class LobotocraftJadePlugin implements IWailaPlugin {

    // 插件ID
    public static final ResourceLocation QLIPHOTH_COUNTER = 
            ResourceUtil.createInstance("qliphoth_counter");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 注册服务端数据提供器
        registration.registerEntityDataProvider(
                AbnormalityDataProvider.INSTANCE, 
                AbstractAbnormality.class
        );
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 注册客户端组件提供器
        registration.registerEntityComponent(
                AbnormalityComponentProvider.INSTANCE, 
                AbstractAbnormality.class
        );
        registration.addTooltipCollectedCallback(1000, (tooltip, accessor) -> {
            if (CoreSuppressionClientState.isActive(CoreSuppressionType.YESOD)
                    && accessor instanceof EntityAccessor entityAccessor
                    && entityAccessor.getEntity() instanceof LivingEntity) {
                tooltip.remove(Identifiers.MC_ENTITY_HEALTH);
                tooltip.add(Component.literal("??? / ???"), Identifiers.MC_ENTITY_HEALTH);
            }
        });
    }
}
