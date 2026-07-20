package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import com.wzz.lobotocraft.entity.EntitySmilingCorpseMountainShockWave;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmilingCorpseMountainWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 4.0;

    // ===== 腐败效果参数 =====
    /** 最高层数 = 5 (amplifier 0..4 表示 1..5 层) */
    private static final int MAX_CORRUPTION_AMP = 4;
    /** 每层持续 10 秒 */
    private static final int CORRUPTION_DURATION = 200;

    // ===== 特殊攻击参数 =====
    /** 特殊攻击基础黑伤(已是"翻倍"后的值 16*2=32) */
    private static final float SPECIAL_BASE_DAMAGE = 32F;
    /** 每层腐败使特殊攻击伤害 +20% */
    private static final float CORRUPTION_DAMAGE_PER_STACK = 0.20F;
    /** 特殊攻击作用的小范围半径 */
    private static final double SPECIAL_AOE_RADIUS = 3.0D;

    public SmilingCorpseMountainWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(16),
                MathUtil.toSpeedModifier(0.8f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        float damage = 16;
        target.hurt(DamageHelper.getDamage(player, "black"), damage);
        // 普攻施加 1 层腐败(封顶刷新)
        applyCorruption(target);
        SoundUtil.playSound(player, ModSounds.SMILING_CORPSE_MOUNTAIN_NORMAL_ATTACK.get());
        return true;
    }

    /** 给目标叠 1 层腐败,最高 5 层,达到上限后仅刷新持续时间。 */
    private static void applyCorruption(LivingEntity target) {
        MobEffectInstance current = target.getEffect(ModMobEffects.CORRUPTION.get());
        int nextAmp = current == null ? 0 : Math.min(current.getAmplifier() + 1, MAX_CORRUPTION_AMP);
        // ambient=false, showParticles=false(黑色粒子由事件手动生成), showIcon=true
        target.addEffect(new MobEffectInstance(
                ModMobEffects.CORRUPTION.get(), CORRUPTION_DURATION, nextAmp, false, false, true));
    }

    @Override
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 40;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isOnCooldown(player, stack)) {
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + getRemainingCooldown(player, stack) + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        startCooldown(player, stack);
        if (!level.isClientSide) {
            triggerAttackAnimation(player, stack);
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                    @Override
                    public void onEnd(@NotNull LivingEntity entity) {
                        SoundUtil.playSound(player, ModSounds.SMILING_CORPSE_MOUNTAIN_SPECIAL_ATTACK.get());
                        resolveSpecialAttack(player, entity);
                    }
                };
                timerEntry.addSkillTimer(target, 500, 310, 1);
            }
        }
        return super.use(level, player, hand);
    }

    /**
     * 特殊攻击落点结算(在延时结束后主线程执行):
     *  1. 落点脚下生成冲击波实体;
     *  2. 对落点小范围内每个生物:
     *     - 主伤害 = 32 * (1 + 0.2 * 腐败层数);
     *     - 全套(isFullEGO)且带腐败时: 每层额外一次 32 基础黑伤(饰品效果);
     *     - 全套且带腐败时: 施加 -70% 移速 5 秒(饰品效果);
     *     - 清除该目标身上全部腐败。
     *  (饰品"每清墓碑 +1% 伤害"由 SmilingCorpseMountainEvent 在 LivingHurtEvent 里统一叠加)
     */
    private static void resolveSpecialAttack(Player player, LivingEntity primaryTarget) {
        Level level = player.level();
        Vec3 impact = primaryTarget.position();

        // 落点脚下的魔法阵冲击波
        spawnShockWave(level, impact);

        boolean fullEgo = EgoArmorHelper.isFullEGO(player, "smiling_corpse_mountain");

        AABB area = new AABB(
                impact.x - SPECIAL_AOE_RADIUS, impact.y - SPECIAL_AOE_RADIUS, impact.z - SPECIAL_AOE_RADIUS,
                impact.x + SPECIAL_AOE_RADIUS, impact.y + SPECIAL_AOE_RADIUS, impact.z + SPECIAL_AOE_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive());
        // 主目标可能因过滤/边界未被 getEntitiesOfClass 命中,兜底补上
        if (primaryTarget.isAlive() && !targets.contains(primaryTarget)) {
            targets.add(primaryTarget);
        }

        for (LivingEntity t : targets) {
            MobEffectInstance corr = t.getEffect(ModMobEffects.CORRUPTION.get());
            int stacks = corr == null ? 0 : corr.getAmplifier() + 1; // 0..5

            // 主特殊伤害(含腐败增伤)
            float mainDamage = SPECIAL_BASE_DAMAGE * (1F + CORRUPTION_DAMAGE_PER_STACK * stacks);
            EntityUtil.clearHurtTime(t);
            t.hurt(DamageHelper.getDamage(player, "black"), mainDamage);

            if (fullEgo && stacks > 0) {
                // 饰品: 每层腐败额外一次基础黑伤
                for (int i = 0; i < stacks; i++) {
                    EntityUtil.clearHurtTime(t);
                    t.hurt(DamageHelper.getDamage(player, "black"), SPECIAL_BASE_DAMAGE);
                }
                // 饰品: -70% 移速 5 秒(命中刷新)
                CorruptionSlowTimer.apply(t);
            }

            // 特殊攻击后清除该目标全部腐败
            if (corr != null) {
                t.removeEffect(ModMobEffects.CORRUPTION.get());
            }
        }
    }

    private static void spawnShockWave(Level level, Vec3 impact) {
        EntitySmilingCorpseMountainShockWave wave =
                new EntitySmilingCorpseMountainShockWave(
                        ModEntities.smiling_corpse_mountain_shock_wave.get(), level);
        wave.setPos(impact.x, impact.y + 0.02D, impact.z);
        level.addFreshEntity(wave);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 5);
        map.put("TemperanceLevel", 5);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public String weaponName() {
        return "smiling_corpse_mountain";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§6※这把武器每次攻击会给予对方“腐败”效果，提高特殊攻击的伤害。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7锤子上有某个员工的苍白脸庞，以及...一张巨口。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7当你挥动这把武器时，你就像一只扑向猎物的苍鹰。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※每次普通攻击为对方施加1层腐败（最高5层，持续10秒，满层刷新），每层使特殊攻击伤害+20%。"));
            p_41423_.add(Component.literal("§6※特殊攻击造成翻倍的黑色伤害（32点）。使用后清除对方全部腐败。"));
        }
    }
}
