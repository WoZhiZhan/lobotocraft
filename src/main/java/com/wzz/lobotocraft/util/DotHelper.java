package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 统一的 DOT（持续伤害）结算入口。所有 DOT 来源都必须走这里
 * （血之渴望的流血、蜂后的孢子、因乐颠狂……），这样：
 *
 * 1. DOT = 真实伤害：无视护甲、护甲韧性、保护附魔、抗性提升药水，
 *    也无视本模组各套装的减伤/增伤。做法是在 LivingHurtEvent(LOWEST) 记录
 *    “该吃到的最终值”，再在 LivingDamageEvent(LOWEST，此时原版减伤全部算完)
 *    把伤害强制改回该值。
 * 2. 颜色抗性（ModAttributes.*_DAMAGE_RESISTANCE）默认仍然生效；
 *    只有蜂后套装的第二条效果会让它对 DOT 失效。
 * 3. 统一标记：{@link #isDotDamage(LivingEntity)} 可以在任意伤害事件里
 *    判断“这次伤害是不是 DOT”，【威胁】的 DOT 增伤就是基于它做的。
 *
 * 用法：
 * <pre>
 *   DotHelper.dealDotDamage(target, DamageHelper.getDamage(attacker, "red"), 8f);
 * </pre>
 */
@Mod.EventBusSubscriber
public class DotHelper {

    /** 正在结算 DOT 的标记 */
    private static final String TAG_DOT = "LobotocraftDotDamage";
    /** 本次 DOT 应该造成的真实伤害（LivingHurtEvent 结束时记录） */
    private static final String TAG_DOT_AMOUNT = "LobotocraftDotAmount";
    /** 本次 DOT 是否无视颜色抗性（蜂后套装） */
    private static final String TAG_DOT_IGNORE_RES = "LobotocraftDotIgnoreResistance";

    public static final float MENACE_DOT_BONUS = 4.0f;

    /** 该实体此刻是否正在承受 DOT 伤害 */
    public static boolean isDotDamage(LivingEntity target) {
        return target != null && target.getPersistentData().getBoolean(TAG_DOT);
    }

    /** 本次 DOT 是否无视目标的颜色抗性 */
    public static boolean isIgnoringResistance(LivingEntity target) {
        return target != null && target.getPersistentData().getBoolean(TAG_DOT_IGNORE_RES);
    }

    /**
     * 目标当前的 DOT 增伤倍率（1.0 = 无加成）。
     * 以后再加“增强DOT”的效果，只改这里，所有 DOT 来源自动生效。
     */
    public static float getDotMultiplier(LivingEntity target) {
        float multiplier = 1.0f;
        if (target == null) return multiplier;

        // 威胁：受到 DOT 伤害 +500%（按层数）
        MobEffectInstance menace = target.getEffect(ModEffects.MENACE.get());
        if (menace != null) {
            multiplier += MENACE_DOT_BONUS * (menace.getAmplifier() + 1);
        }
        return multiplier;
    }

    /** 取得目标对这类颜色伤害的抗性（没有对应属性时返回 1.0 = 正常受伤） */
    public static float getColorResistance(LivingEntity target, DamageSource source) {
        AttributeInstance attr = null;
        if (DamageHelper.isRedDamage(source)) {
            attr = target.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isWhiteDamage(source)) {
            attr = target.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isBlackDamage(source)) {
            attr = target.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isBlueDamage(source)) {
            attr = target.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        }
        return attr == null ? 1.0f : (float) attr.getValue();
    }

    /**
     * 蜂后套装第二条：攻击带【威胁】的目标时，若目标对应伤害抗性 > 0.0，
     * 则所有 DOT 伤害不再受抗性影响。
     */
    public static boolean shouldIgnoreResistance(LivingEntity target, DamageSource source) {
        if (target == null || source == null) return false;
        if (!(source.getEntity() instanceof Player attacker)) return false;
        if (!EgoArmorHelper.isFullEGO(attacker, "queen_bee")) return false;
        if (!target.hasEffect(ModEffects.MENACE.get())) return false;
        return getColorResistance(target, source) > 0.0f;
    }

    /**
     * 结算一次 DOT 伤害。
     *
     * @param target     目标
     * @param source     伤害来源（颜色伤害照常用 DamageHelper.getDamage(player, "red") 等）
     * @param baseAmount 基础伤害（增伤倍率之前）
     */
    public static void dealDotDamage(LivingEntity target, DamageSource source, float baseAmount) {
        if (target == null || source == null) return;
        if (target.level().isClientSide || !target.isAlive() || baseAmount <= 0f) return;

        float amount = baseAmount * getDotMultiplier(target);

        CompoundTag data = target.getPersistentData();
        data.putBoolean(TAG_DOT, true);
        data.putBoolean(TAG_DOT_IGNORE_RES, shouldIgnoreResistance(target, source));
        data.putFloat(TAG_DOT_AMOUNT, amount);

        // DOT 无视无敌帧，结算完把无敌帧还原，避免把玩家自己的普攻“吃掉”
        int invulnerableTime = target.invulnerableTime;
        int hurtTime = target.hurtTime;
        try {
            target.invulnerableTime = 0;
            target.hurt(source, amount);
        } finally {
            target.invulnerableTime = invulnerableTime;
            target.hurtTime = hurtTime;
            data.remove(TAG_DOT);
            data.remove(TAG_DOT_IGNORE_RES);
            data.remove(TAG_DOT_AMOUNT);
        }
    }

    /** LivingHurtEvent 末尾：记录“允许的修正”都算完之后的最终值（含颜色抗性） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!isDotDamage(target)) return;
        target.getPersistentData().putFloat(TAG_DOT_AMOUNT, event.getAmount());
    }

    /** 真伤的落地点：护甲/附魔/抗性药水都算完了，直接把伤害改回真实值 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!isDotDamage(target)) return;

        float trueAmount = target.getPersistentData().getFloat(TAG_DOT_AMOUNT);
        if (trueAmount > 0f && event.getAmount() != trueAmount) {
            event.setAmount(trueAmount);
        }
    }
}