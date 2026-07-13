package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 浊心斯卡蒂"同葬无光之愿"祝福相关事件:
 *  - 减少受到四种颜色伤害 40%
 *  - 持有祝福的玩家死亡时,原地满血免死复活,斯卡蒂计数器 -1,并播放音频
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SkadiEvent {

    /** 机制2:被祝福玩家受到四种颜色(WHITE/RED/BLACK/PALE)的伤害减少 40% */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())) {
            // mod 的异想体伤害体系即"四种颜色"伤害,这里对所有进入该事件的伤害统一减免 40%
            event.setAmount(event.getAmount() * 0.6f);
        }
        if (player.level().isClientSide) return;
        if (event.getAmount() < event.getEntity().getHealth()) return;
        if (!player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())) return;

        // 取消死亡,满血复活
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        // 清除可能导致再次死亡的负面效果,但保留祝福本身
        player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
        player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        player.setRemainingFireTicks(0);
        // 播放复活音频
        player.level().playSound(null, player.blockPosition(),
                ModSounds.SKADI_AMBIENT.get(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.sendSystemMessage(Component.literal("§9「同葬无光之愿」庇护了你,你免于死亡。"));

        // 找到该维度内的斯卡蒂,计数器 -1(并由斯卡蒂处理归零逻辑)
        ServerLevel level = (ServerLevel) player.level();
        List<EntityDarkSkadi> skadis = level.getEntitiesOfClass(EntityDarkSkadi.class,
                player.getBoundingBox().inflate(4096), LivingEntity::isAlive);
        if (!skadis.isEmpty()) {
            skadis.get(0).onBlessedPlayerRevive(player);
        }
    }
}
