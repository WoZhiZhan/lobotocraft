package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.blue_star.BlueStarBaseArmor;
import com.wzz.lobotocraft.item.ego.blue_star.BlueStarWeapon;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 新星之声 套装相关效果:
 *  1) 护甲(穿戴胸甲即可,房间回精神为护甲固有效果):每5秒为周围所有玩家(含自己)恢复5点精神(恐慌者无效)。
 *  2) 套装(武器+护甲+饰品):命中目标减速30%(由武器写入 persistentData,这里维护减速效果)。
 *  3) 套装:受到致命伤害时强行保留1%生命与精神原地复活一次(每条命一次),播放碧蓝新星死亡音效。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class BlueStarSetEvent {

    private static final String REVIVE_USED = "blue_star_revive_used";

    /** 是否穿戴新星之声护甲(任意一件即触发房间回精神;此处要求至少胸甲在身) */
    private static boolean wearsBlueStarArmor(Player player) {
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.getItem() instanceof BlueStarBaseArmor) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();

        // 1) 护甲每5秒为同一区域内所有玩家(含自己)恢复5点精神(恐慌者无效)
        if (wearsBlueStarArmor(player) && now % 100 == 0) {
            for (ServerPlayer p : player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                    player.getBoundingBox().inflate(16))) {
                // 恐慌(精神<=0)者无效
                if (MentalValueUtil.getMentalValue(p) > 0) {
                    MentalValueUtil.addMentalValue(p, 5f);
                }
            }
        }

        // 复活标记:满血时重置(视为"每条命一次")
        if (player.getHealth() >= player.getMaxHealth()
                && player.getPersistentData().getBoolean(REVIVE_USED)) {
            player.getPersistentData().putBoolean(REVIVE_USED, false);
        }
    }

    /** 套装:命中目标减速30%(目标侧维护) */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        long until = entity.getPersistentData().getLong("blue_star_slow_until");
        if (until > entity.level().getGameTime()) {
            // 减速30%(缓慢约对应等级,每tick刷新短时效果)
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false, false));
        }
    }

    /** 套装:致命伤害复活(优先级最低,在伤害最终结算前拦截) */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!BlueStarWeapon.isBlueStarSet(player)) return;
        if (player.getPersistentData().getBoolean(REVIVE_USED)) return;

        float remaining = player.getHealth() - event.getAmount();
        if (remaining > 0) return; // 非致命

        // 拦截致命:取消本次伤害,强行保留1%生命与精神
        event.setCanceled(true);
        float oneHpPct = Math.max(1f, player.getMaxHealth() * 0.01f);
        player.setHealth(oneHpPct);
        float maxMental = MentalValueUtil.getEffectiveMaxMentalValue(player);
        MentalValueUtil.setMentalValue(player, Math.max(1f, maxMental * 0.01f));
        player.getPersistentData().putBoolean(REVIVE_USED, true);

        // 碧蓝新星死亡音效 + 短暂无敌
        player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.BLUE_STAR_DIE.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
        player.invulnerableTime = 40;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§b新星之声：自绝望中重燃！"), true);
    }
}
