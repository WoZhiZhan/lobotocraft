package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityPunishingBird;
import com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary;
import com.wzz.lobotocraft.event.abnormality.AbnormalityEscapeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 小红帽雇佣兵的管理须知Ⅲ:
 * 每当有异想体突破收容时,"小红帽雇佣兵"的逆卡巴拉计数器立刻减少。
 * 但她不会理睬惩戒鸟(O-02-56)的出逃。
 *
 * 狼(F-02-58)相关须知(Ⅱ/Ⅳ/Ⅴ/Ⅵ)与狂暴机制在狼实装后接入。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class RedHoodEvent {

    @SubscribeEvent
    public static void onAbnormalityEscape(AbnormalityEscapeEvent event) {
        com.wzz.lobotocraft.entity.base.AbstractAbnormality escaped = event.getAbnormality();
        if (escaped == null || escaped.level().isClientSide) return;
        // 不理睬惩戒鸟的出逃
        if (escaped instanceof EntityPunishingBird) return;
        // 自己出逃不触发
        if (escaped instanceof EntityRedHoodMercenary) return;
        if (!(escaped.level() instanceof ServerLevel level)) return;

        for (EntityRedHoodMercenary redhat : level.getEntitiesOfClass(EntityRedHoodMercenary.class,
                escaped.getBoundingBox().inflate(2048),
                r -> r.isAlive() && !r.hasEscape())) {
            redhat.decreaseQliphothCounter(1);
        }
    }
}
