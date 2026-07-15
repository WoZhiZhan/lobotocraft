package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.redhat_mercenary.RedhatMercenaryWeaponGun;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 羊皮（sheepskin）饰品套装
 * 单件效果：
 *   • 穿戴“郁蓝创痕”或“猩红创痕”时，造成的伤害 +15%
 * 套装（郁蓝创痕饰品 + 猩红创痕饰品 + 羊皮饰品，且装备成套并锁定）：
 *   1. 红/白/黑抗性改为 0.6，蓝抗性改为 0.8
 *   2. 穿“郁蓝创痕”：潜狼强化 —— 累计损失 10% 生命值后进入潜狼模式 6 秒，
 *      期间免疫一切伤害，且造成的红色伤害 +6
 *   3. 穿“猩红创痕”：暴怒（血量 < 50%）时攻速 +25%、火铳 CD 降至 1.5s；
 *      对【猎物】伤害额外 +3；击杀【猎物】回复 5% 生命值与精神值
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SheepskinSetHandler {

    /* ============ 参数 ============ */
    /** 单件：伤害 +15% */
    private static final float SINGLE_DAMAGE_BONUS = 1.15f;

    /** 套装抗性覆盖值 */
    private static final double RES_RWB = 0.6D;
    private static final double RES_BLUE = 0.8D;

    /** 潜狼：累计损失 10% 生命值触发 */
    private static final float LURK_TRIGGER_RATIO = 0.10f;
    /** 潜狼：持续 6 秒 */
    private static final int LURK_DURATION_TICKS = 6 * 20;
    /** 潜狼：红色伤害 +6 */
    private static final float LURK_RED_BONUS = 6.0f;

    /** 暴怒阈值 */
    private static final float FURY_HEALTH_RATIO = 0.5f;
    /** 暴怒：攻速 +25% */
    private static final double FURY_ATTACK_SPEED = 0.25D;
    /** 暴怒：火铳 CD 1.5 秒 */
    public static final int FURY_GUN_COOLDOWN_TICKS = 30;
    /** 对猎物伤害 +3 */
    private static final float PREY_DAMAGE_BONUS = 3.0f;
    /** 击杀猎物回复比例 */
    private static final float PREY_KILL_RESTORE_RATIO = 0.05f;

    /* ============ NBT / 属性 UUID ============ */
    /** 潜狼：累计已损失的生命值 */
    private static final String TAG_LURK_ACCUM = "SheepskinLurkAccum";
    /** 潜狼：结束的游戏时刻 */
    private static final String TAG_LURK_UNTIL = "SheepskinLurkUntil";

    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("fbd2f9c9-b305-4bfc-8584-d4378fa5b7d5");
    private static final UUID RED_RES_UUID = UUID.fromString("3a9742f4-ce9f-415b-90ae-7304b1c2e704");
    private static final UUID WHITE_RES_UUID = UUID.fromString("38073f85-5631-4221-b29c-1487b596c15d");
    private static final UUID BLACK_RES_UUID = UUID.fromString("3fd6543e-8a89-49d5-8aef-b078e9c2897b");
    private static final UUID BLUE_RES_UUID = UUID.fromString("1478afbb-3f5f-4a27-b46d-6fd5e03a2fb9");

    /* ================================================================
     * 套装判定
     * ================================================================ */

    /** 三个饰品都戴着 */
    private static boolean hasAllThreeCurios(Player player) {
        return CuriosUtil.hasCurios(player, ModItems.SHEEPSKIN_CURIO.get())
                && CuriosUtil.hasCurios(player, ModItems.BIG_BADWOLF_CURIO.get())
                && CuriosUtil.hasCurios(player, ModItems.REDHAT_MERCENARY_CURIO.get());
    }

    /** 穿着“郁蓝创痕”成套 + 饰品锁定 */
    public static boolean isBigBadwolfSetActive(LivingEntity living) {
        return living instanceof Player player
                && hasAllThreeCurios(player)
                && EgoArmorHelper.isFullSetWithCurioLocked(player, "big_badwolf");
    }

    /** 穿着“猩红创痕”成套 + 饰品锁定 */
    public static boolean isRedhatSetActive(LivingEntity living) {
        return living instanceof Player player
                && hasAllThreeCurios(player)
                && EgoArmorHelper.isFullSetWithCurioLocked(player, "redhat_mercenary");
    }

    /**
     * 饰品套装是否生效（第1条：抗性覆盖）。
     * 两套装备不可能同时穿满，所以这里按“穿着其中任意一套”处理；
     * 如果你的设定是必须同时满足，把 || 改成 &&。
     */
    public static boolean isCurioSetActive(LivingEntity living) {
        return isBigBadwolfSetActive(living) || isRedhatSetActive(living);
    }

    /** 单件效果：戴着羊皮 + 穿着任一套装备（不要求锁定/三饰品） */
    private static boolean hasSheepskinWithArmor(Player player) {
        return CuriosUtil.hasCurios(player, ModItems.SHEEPSKIN_CURIO.get())
                && (EgoArmorHelper.isFullEGO(player, "big_badwolf")
                || EgoArmorHelper.isFullEGO(player, "redhat_mercenary"));
    }

    /** 暴怒状态 */
    public static boolean isFury(Player player) {
        return player.getHealth() < player.getMaxHealth() * FURY_HEALTH_RATIO;
    }

    /** 潜狼模式中 */
    public static boolean isLurking(Player player) {
        return player.getPersistentData().getLong(TAG_LURK_UNTIL) > player.level().getGameTime();
    }

    /** 火铳冷却：暴怒 + 猩红套装 = 1.5秒，否则用默认值 */
    public static int getGunCooldown(Player player, int defaultCooldown) {
        return (isRedhatSetActive(player) && isFury(player)) ? FURY_GUN_COOLDOWN_TICKS : defaultCooldown;
    }

    /* ================================================================
     * 每 tick：属性（抗性覆盖 / 暴怒攻速）
     * ================================================================ */

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;

        // 第1条：抗性覆盖
        boolean setActive = isCurioSetActive(player);
        overrideResistance(player, ModAttributes.RED_DAMAGE_RESISTANCE.get(), RED_RES_UUID, setActive, RES_RWB);
        overrideResistance(player, ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), WHITE_RES_UUID, setActive, RES_RWB);
        overrideResistance(player, ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), BLACK_RES_UUID, setActive, RES_RWB);
        overrideResistance(player, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), BLUE_RES_UUID, setActive, RES_BLUE);

        // 第3条：暴怒攻速 +25%
        boolean furyAttackSpeed = isRedhatSetActive(player) && isFury(player);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            AttributeModifier existing = attackSpeed.getModifier(ATTACK_SPEED_UUID);
            if (furyAttackSpeed && existing == null) {
                attackSpeed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_UUID,
                        "sheepskin_fury_attack_speed", FURY_ATTACK_SPEED,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!furyAttackSpeed && existing != null) {
                attackSpeed.removeModifier(ATTACK_SPEED_UUID);
            }
        }

        // 潜狼累计值：没穿郁蓝套装就清掉，免得脱下来还留着
        if (!isBigBadwolfSetActive(player)) {
            player.getPersistentData().remove(TAG_LURK_ACCUM);
        }
    }

    /**
     * 抗性是“覆盖成某个值”，而修饰符是加法。
     * 做法：先摘掉自己的修饰符，读到“没有我时的值”，再补上差额，最终值恰好等于目标值。
     */
    private static void overrideResistance(Player player, Attribute attribute, UUID uuid,
                                           boolean active, double target) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        if (instance.getModifier(uuid) != null) {
            instance.removeModifier(uuid);
        }
        if (!active) return;

        double without = instance.getValue();
        double delta = target - without;
        if (Math.abs(delta) < 1.0E-4D) return;
        instance.addTransientModifier(new AttributeModifier(uuid,
                "sheepskin_resistance_override", delta, AttributeModifier.Operation.ADDITION));
    }

    /* ================================================================
     * 伤害相关
     * ================================================================ */

    /** 潜狼模式：免疫一切伤害 */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide
                && isLurking(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        /* ---------- 攻击方 ---------- */
        if (event.getSource().getEntity() instanceof Player attacker) {

            // 单件：穿着任一套装备时，造成的伤害 +15%
            if (hasSheepskinWithArmor(attacker)) {
                event.setAmount(event.getAmount() * SINGLE_DAMAGE_BONUS);
            }

            // 套装第2条：潜狼模式中，造成的红色伤害 +6
            if (target instanceof Player player && isBigBadwolfSetActive(player) && !isLurking(player)) {
                float accum = player.getPersistentData().getFloat(TAG_LURK_ACCUM) + event.getAmount();
                if (accum >= lurkThreshold(player)) {
                    accum = 0f;
                    enterLurk(player);
                }
                player.getPersistentData().putFloat(TAG_LURK_ACCUM, accum);
            }

            // 套装第3条：对【猎物】伤害额外 +3
            if (isRedhatSetActive(attacker)
                    && RedhatMercenaryWeaponGun.Prey.isPreyOf(attacker, target)) {
                event.setAmount(event.getAmount() + PREY_DAMAGE_BONUS);
            }
        }

        /* ---------- 受击方 ---------- */
        // 套装第2条：累计损失 10% 生命值 -> 进入潜狼模式 6 秒
        if (target instanceof Player player && isBigBadwolfSetActive(player) && !isLurking(player)) {
            float accum = player.getPersistentData().getFloat(TAG_LURK_ACCUM) + event.getAmount();
            float threshold = player.getMaxHealth() * LURK_TRIGGER_RATIO;
            if (accum >= threshold) {
                accum = 0f;
                player.getPersistentData().putLong(TAG_LURK_UNTIL,
                        player.level().getGameTime() + LURK_DURATION_TICKS);
            }
            player.getPersistentData().putFloat(TAG_LURK_ACCUM, accum);
        }
    }

    /** 潜狼触发阈值：按基础最大生命值算，避免临时 MAX_HEALTH 修饰符导致阈值忽大忽小 */
    private static float lurkThreshold(Player player) {
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        double base = hp != null ? hp.getValue() : player.getMaxHealth();
        return (float) (base * LURK_TRIGGER_RATIO);
    }

    /** 进入潜狼：记录结束时刻 + 音效 + 持续粒子 */
    private static void enterLurk(Player player) {
        player.getPersistentData().putLong(TAG_LURK_UNTIL,
                player.level().getGameTime() + LURK_DURATION_TICKS);
        SoundUtil.playSound(player, ModSounds.BIG_BADWOLF_CURIO.get());

        // 6秒内每秒撒一次 END_ROD 粒子；靠 isLurking 自动停
        LurkParticleTimer timer = new LurkParticleTimer();
        timer.addSkillTimer(player, 0, LURK_DURATION_TICKS * 50, 1, true);
    }

    public static class LurkParticleTimer extends TimerEntry<Player> {
        @Override
        public void onRunning(@NotNull Player player) {
            if (isLurking(player)) {
                ParticleUtil.spawnParticlesAroundEntity(player, ParticleTypes.END_ROD, 20, 0.01f);
            }
        }
    }

    /** 套装第3条：击杀【猎物】回复 5% 生命值与精神值 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!isRedhatSetActive(player)) return;
        if (!RedhatMercenaryWeaponGun.Prey.isPreyOf(player, dead)) return;

        player.heal(player.getMaxHealth() * PREY_KILL_RESTORE_RATIO);
        MentalValueUtil.addMentalValue(player,
                MentalValueUtil.getEffectiveMaxMentalValue(player) * PREY_KILL_RESTORE_RATIO);
    }
}