package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.util.DamageHelper;
import com.lobotocraft.facility.day.FacilityDayCommittedEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 浊心斯卡蒂"同葬无光之愿"祝福相关事件:
 *  - 减少受到四种颜色伤害 40%
 *  - 持有祝福的玩家死亡时,原地满血免死复活,斯卡蒂计数器 -1,并播放音频
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SkadiEvent {
    private static final String LAST_REVIVE_TICK_TAG = "lobotocraft_wish_without_light_revive_tick";

    /** Shared pre-channel reduction, so WHITE/BLACK mental damage receives the same blessing. */
    public static float incomingDamageMultiplier(LivingEntity target, DamageSource source) {
        if (!(target instanceof ServerPlayer player)
                || !player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())
                || !SkadiBlessingManager.isValid(player)) return 1;
        return DamageHelper.isRedDamage(source) || DamageHelper.isWhiteDamage(source)
                || DamageHelper.isBlackDamage(source) || DamageHelper.isBlueDamage(source) ? .6F : 1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())) {
            SkadiBlessingManager.isValid(player);
        }
    }

    @SubscribeEvent
    public static void onDayAdvance(FacilityDayCommittedEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            SkadiBlessingManager.clear(player);
        }
    }

    /** Final HP revival adapter, invoked by DamageSettlement after ordinary protection. */
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())) return;
        if (!SkadiBlessingManager.isValid(player)) return;
        if (!isColoredDamage(event)) return;

        float damage = event.getAmount();

        // DamageSettlement already applied the 40% to both channels exactly once.
        // This adapter only handles final post-vanilla health lethality.
        if (damage >= player.getHealth()) {
            if (!SkadiBlessingManager.tryConsumeCharge(player)) return;
            event.setCanceled(true);
            revivePlayer(player);
        } else {
            // 非致命伤害 → 应用减免后的伤害
            event.setAmount(damage);
        }
    }

    /**
     * 兜底处理直接 setHealth(0)、die() 等绕过 LivingDamageEvent 的机制杀。
     * 在死亡流程创建新玩家实体前取消事件，避免员工属性和公司天数被重置。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean revivedEarlierThisTick = player.getPersistentData().contains(LAST_REVIVE_TICK_TAG)
                && player.getPersistentData().getLong(LAST_REVIVE_TICK_TAG) == player.level().getGameTime();
        // Canceling the real death below plus the Clone capability copy likely prevents Skadi revival from
        // resetting employee stats (reported issue 22), but this still needs a runtime regression test.
        if (revivedEarlierThisTick) {
            event.setCanceled(true);
            revivePlayer(player);
            return;
        }
        if (!player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get())
                || !SkadiBlessingManager.tryConsumeCharge(player)) return;
        event.setCanceled(true);
        revivePlayer(player);
    }

    private static void revivePlayer(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        boolean firstReviveThisTick = !player.getPersistentData().contains(LAST_REVIVE_TICK_TAG)
                || player.getPersistentData().getLong(LAST_REVIVE_TICK_TAG) != gameTime;

        player.setHealth(player.getMaxHealth());
        player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
        player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        player.setRemainingFireTicks(0);

        // 有些机制先造成致命伤害，再在同一 tick 直接调用 die()；只消耗一次祝福。
        if (!firstReviveThisTick) return;
        player.getPersistentData().putLong(LAST_REVIVE_TICK_TAG, gameTime);

        player.level().playSound(null, player.blockPosition(),
                ModSounds.SKADI_AMBIENT.get(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.skadi.protected_from_death"));

    }

    private static boolean isColoredDamage(LivingDamageEvent event) {
        return DamageHelper.isRedDamage(event.getSource())
                || DamageHelper.isWhiteDamage(event.getSource())
                || DamageHelper.isBlackDamage(event.getSource())
                || DamageHelper.isBlueDamage(event.getSource());
    }
}
