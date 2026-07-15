package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())) return;

        float damage = event.getAmount();

        // 1. 减少 40% 伤害
        damage *= 0.6f;

        // 2. 判断减免后是否致命
        if (damage >= player.getHealth()) {
            // 致命伤害 → 触发复活
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
            player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
            player.setRemainingFireTicks(0);
            player.level().playSound(null, player.blockPosition(),
                    ModSounds.SKADI_AMBIENT.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
            player.sendSystemMessage(Component.literal("§9「同葬无光之愿」庇护了你,你免于死亡。"));

            ServerLevel level = (ServerLevel) player.level();
            List<EntityDarkSkadi> skadis = level.getEntitiesOfClass(EntityDarkSkadi.class,
                    player.getBoundingBox().inflate(4096), LivingEntity::isAlive);
            if (!skadis.isEmpty()) {
                skadis.get(0).onBlessedPlayerRevive(player);
            }
        } else {
            // 非致命伤害 → 应用减免后的伤害
            event.setAmount(damage);
        }
    }
}
