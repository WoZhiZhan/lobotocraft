package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * E.G.O装备Tick处理器
 * 在玩家tick中检测是否穿戴完整套装，并触发套装的被动效果
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class EgoArmorHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在服务端处理，并且在tick结束时处理
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }

        Player player = event.player;
        
        // 每tick应用装备抗性（检查装备锁定状态并应用/恢复抗性）
        EgoArmorHelper.applyArmorResistances(player);
        
        // 每20tick（1秒）检查一次套装效果，避免过于频繁
        if (player.tickCount % 20 == 0) {
            // 触发E.G.O套装的tick效果
            EgoArmorHelper.tickSetEffects(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            if (event.getSource() != null && event.getSource().getEntity() instanceof Player player) {
                EgoArmorHelper.triggerAttackEffect(player, event.getEntity(), event.getAmount());
            }
            return;
        }
        DamageSource source = event.getSource();

        // 判断伤害类型
        String damageType = getDamageType(source);
        if (damageType == null) {
            return;  // 不是自定义伤害类型，不处理
        }
        EgoArmorHelper.triggerDamageEffect(player, damageType, event.getAmount());

    }

    /**
     * 从DamageSource获取伤害类型
     *
     * @param source 伤害源
     * @return 伤害类型字符串（"red", "white", "black", "pale"），如果不是自定义伤害则返回null
     */
    private static String getDamageType(DamageSource source) {
        if (DamageHelper.isRedDamage(source)) {
            return "red";
        } else if (DamageHelper.isWhiteDamage(source)) {
            return "white";
        } else if (DamageHelper.isBlackDamage(source)) {
            return "black";
        } else if (DamageHelper.isBlueDamage(source)) {
            return "pale";  // PALE对应BLUE
        }
        return null;
    }
}