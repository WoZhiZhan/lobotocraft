package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.UUID;

/**
 * DOT（持续伤害）总入口：既是“怎么结算”的规则，也是“谁在流血”的容器。
 *
 * 【真实伤害】
 * DOT 无视护甲、护甲韧性、保护附魔、抗性提升药水，也无视本模组各套装的增伤/减伤。
 * 做法：LivingHurtEvent(LOWEST) 记录“该吃到的最终值”，
 *      LivingDamageEvent(LOWEST，原版减伤已全部算完) 把伤害强制改回该值。
 * 颜色抗性（ModAttributes.*_DAMAGE_RESISTANCE）默认仍然生效，
 * 只有蜂后套装第二条会让它对 DOT 失效（{@link #shouldIgnoreResistance}）。
 *
 * 【通用 DOT 容器】
 * 简单的“每 X tick 掉 Y 点，持续 Z tick，可叠 N 层”的 DOT 全部走
 * {@link #applyDot}，由本类统一 tick，不需要每个来源自己写一套计时器：
 * <pre>
 *   // 血之渴望：每秒3点红伤/层，5层，10秒
 *   DotHelper.applyDot(DotHelper.RED_SHOES_BLEED, attacker, target, 3f, "red", 20, 200, 5);
 *   // 荆棘巴士：每秒2点白伤，5秒，不叠层
 *   DotHelper.applyDot(DotHelper.THORN_BUS_DOT, attacker, target, 2f, "white", 20, 100, 1);
 * </pre>
 * 特殊 DOT（比如挂在 MobEffect 上的孢子）可以直接调 {@link #dealDotDamage}。
 */
@Mod.EventBusSubscriber
public class DotHelper {

    /* ================= 结算标记 ================= */
    /** 正在结算 DOT */
    private static final String TAG_DOT = "LobotocraftDotDamage";
    /** 本次 DOT 应该造成的真实伤害 */
    private static final String TAG_DOT_AMOUNT = "LobotocraftDotAmount";
    /** 本次 DOT 是否无视颜色抗性（蜂后套装） */
    private static final String TAG_DOT_IGNORE_RES = "LobotocraftDotIgnoreResistance";

    /* ================= DOT 容器 ================= */
    private static final String TAG_DOTS = "LobotocraftDots";
    private static final String KEY_SOURCE = "src";
    private static final String KEY_DAMAGE = "dmg";
    private static final String KEY_COLOR = "color";
    private static final String KEY_INTERVAL = "interval";
    private static final String KEY_NEXT = "next";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_STACKS = "stacks";
    private static final String KEY_MAX_STACKS = "max";

    /** DOT 的 id（自己加新的就行，互不干扰） */
    public static final String RED_SHOES_BLEED = "red_shoes_bleed";
    public static final String THORN_BUS_DOT = "thorn_bus";

    public static final float MENACE_DOT_BONUS = 4.0f;

    /* ================================================================
     * 施加 / 清除
     * ================================================================ */

    /**
     * 施加（或叠加/刷新）一个 DOT。
     *
     * @param id             DOT 唯一 id，同 id 会叠层/刷新，不同 id 各自独立结算
     * @param attacker       施加者（DOT 伤害的来源）
     * @param target         目标
     * @param damagePerStack 每层每次跳伤的基础伤害
     * @param color          伤害颜色："red" / "white" / "black" / "blue"
     * @param intervalTicks  跳伤间隔（tick）
     * @param durationTicks  持续时间（tick），再次施加会刷新
     * @param maxStacks      最大层数（不叠层就填 1）
     */
    public static void applyDot(String id, Player attacker, LivingEntity target, float damagePerStack,
                                String color, int intervalTicks, int durationTicks, int maxStacks) {
        if (id == null || attacker == null || target == null) return;
        if (target == attacker) return;
        if (target.level().isClientSide || !target.isAlive()) return;
        if (damagePerStack <= 0f || intervalTicks <= 0 || durationTicks <= 0) return;

        CompoundTag dots = target.getPersistentData().getCompound(TAG_DOTS);
        CompoundTag dot = dots.contains(id) ? dots.getCompound(id) : new CompoundTag();

        int stacks = Math.min(Math.max(1, maxStacks), dot.getInt(KEY_STACKS) + 1);
        dot.putInt(KEY_STACKS, stacks);
        dot.putInt(KEY_MAX_STACKS, Math.max(1, maxStacks));
        dot.putFloat(KEY_DAMAGE, damagePerStack);
        dot.putString(KEY_COLOR, color);
        dot.putInt(KEY_INTERVAL, intervalTicks);
        // 命中刷新持续时间，但不重置跳伤计时，避免高攻速刷新导致 DOT 永远不跳/每次命中都跳
        dot.putInt(KEY_DURATION, durationTicks);
        if (dot.getInt(KEY_NEXT) <= 0) {
            dot.putInt(KEY_NEXT, intervalTicks);
        }
        dot.putUUID(KEY_SOURCE, attacker.getUUID());

        dots.put(id, dot);
        target.getPersistentData().put(TAG_DOTS, dots);
    }

    /** 某个 DOT 当前层数（没有则0） */
    public static int getStacks(LivingEntity target, String id) {
        if (target == null) return 0;
        CompoundTag dots = target.getPersistentData().getCompound(TAG_DOTS);
        return dots.contains(id) ? dots.getCompound(id).getInt(KEY_STACKS) : 0;
    }

    public static void clearDot(LivingEntity target, String id) {
        if (target == null) return;
        CompoundTag dots = target.getPersistentData().getCompound(TAG_DOTS);
        dots.remove(id);
        if (dots.isEmpty()) {
            target.getPersistentData().remove(TAG_DOTS);
        } else {
            target.getPersistentData().put(TAG_DOTS, dots);
        }
    }

    public static void clearDots(LivingEntity target) {
        if (target == null) return;
        target.getPersistentData().remove(TAG_DOTS);
    }

    /* ================================================================
     * 结算
     * ================================================================ */

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
        MobEffectInstance menace = target.getEffect(ModMobEffects.MENACE.get());
        if (menace != null) {
            multiplier += MENACE_DOT_BONUS * (menace.getAmplifier() + 1);
        }
        return multiplier;
    }

    /** 目标对这类颜色伤害的抗性（没有对应属性时返回 1.0 = 正常受伤） */
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
     * 蜂后套装第二条：攻击带【威胁】的目标时，若目标对应伤害抗性 &gt; 0.0，
     * 则所有 DOT 伤害不再受抗性影响。
     */
    public static boolean shouldIgnoreResistance(LivingEntity target, DamageSource source) {
        if (target == null || source == null) return false;
        if (!(source.getEntity() instanceof Player attacker)) return false;
        if (!EgoArmorHelper.isFullEGO(attacker, "queen_bee")) return false;
        if (!target.hasEffect(ModMobEffects.MENACE.get())) return false;
        return getColorResistance(target, source) > 0.0f;
    }

    /**
     * 结算一次 DOT 伤害（真实伤害）。
     * 特殊 DOT（例如挂在 MobEffect 上的孢子）直接调这个。
     *
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

    /* ================================================================
     * 事件
     * ================================================================ */

    /** 统一 tick 所有通过 applyDot 施加的 DOT */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        CompoundTag root = target.getPersistentData();
        if (!root.contains(TAG_DOTS)) return;

        if (!target.isAlive()) {
            clearDots(target);
            return;
        }

        CompoundTag dots = root.getCompound(TAG_DOTS);
        boolean changed = false;

        for (String id : new ArrayList<>(dots.getAllKeys())) {
            CompoundTag dot = dots.getCompound(id);

            int duration = dot.getInt(KEY_DURATION) - 1;
            if (duration <= 0) {
                dots.remove(id);
                changed = true;
                continue;
            }
            dot.putInt(KEY_DURATION, duration);

            int next = dot.getInt(KEY_NEXT) - 1;
            if (next > 0) {
                dot.putInt(KEY_NEXT, next);
                dots.put(id, dot);
                changed = true;
                continue;
            }
            dot.putInt(KEY_NEXT, Math.max(1, dot.getInt(KEY_INTERVAL)));
            dots.put(id, dot);
            changed = true;

            Player source = resolveSource(target, dot);
            if (source == null) {
                // 施加者已下线/死亡/不在当前维度：本次跳伤跳过，持续时间照常走
                continue;
            }

            float damage = dot.getFloat(KEY_DAMAGE) * Math.max(1, dot.getInt(KEY_STACKS));
            dealDotDamage(target, DamageHelper.getDamage(source, dot.getString(KEY_COLOR)), damage);

            if (!target.isAlive()) {
                clearDots(target);
                return;
            }
        }

        if (changed) {
            if (dots.isEmpty()) {
                root.remove(TAG_DOTS);
            } else {
                root.put(TAG_DOTS, dots);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        clearDots(event.getEntity());
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

    private static Player resolveSource(LivingEntity target, CompoundTag dot) {
        if (!dot.hasUUID(KEY_SOURCE)) return null;
        UUID uuid = dot.getUUID(KEY_SOURCE);
        Player player = target.level().getPlayerByUUID(uuid);
        return (player != null && player.isAlive()) ? player : null;
    }
}