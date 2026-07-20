package com.wzz.lobotocraft.item.ego.nothing_there;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NothingThereWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 4.0;
    public NothingThereWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(10),
                MathUtil.toSpeedModifier(1.1f),
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
        boolean isFullEGO = EgoArmorHelper.isFullEGO(player, "nothing_there");
        SoundUtil.playSound(player, ModSounds.NOTHING_THERE_WEAPON_NORMAL_ATTACK.get());
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        float damage = 11 + player.random.nextInt(4);
        int frash = 10;
        if (isFullEGO) {
            frash = 50;
        }
        if (frash <= player.random.nextInt(101)) {
            player.displayClientMessage(Component.literal("§a拟态的特殊攻击冷却刷新了！"), true);
            resetCooldown(stack);
        }
        target.hurt(target.damageSources().playerAttack(player), damage);
        if (!isFullEGO) {
            player.heal(damage * 0.5f);
        } else {
            player.heal(damage);
        }
        return true;
    }

    @Override
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return EgoArmorHelper.isFullEGO(player, "nothing_there") ? 100 : 200;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (isOnCooldown(player, stack)) {
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + getRemainingCooldown(player, stack) + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        startCooldown(player, stack);
        if (!level.isClientSide) {
            triggerAttackAnimation(player, stack);
            for (LivingEntity target : EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE)) {
                DamageTimer.addNewTimer(target, DamageHelper.getDamage(player, "red"), player);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 5);
        map.put("FortitudeLevel", 5);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public String weaponName() {
        return "nothing_there";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§6※这把武器每次攻击都有概率刷新特殊攻击的冷却，造成伤害的25%会转化成生命值。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7对这种怪异的类人型生物的组织仿制是失败的，"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7当那武器上不可名状的眼睛注视着你的时候，你的身体会不由得颤抖起来。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7若持有者能够将这把武器的能力发挥到极致，那么挥舞它足以造成毁灭性的后果。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※这把武器在攻击时有10%的概率刷新特殊攻击的CD，持有者高举大刀并重劈，对目标造成40到90点物理伤害。"));
            p_41423_.add(Component.literal("§6※持有者使用这把武器造成伤害时，会得到等同于伤害量25%的生命值回复。"));
        }
    }

    public static class DamageTimer extends TimerEntry<LivingEntity> {
        private final Player attacker;
        private final DamageSource damageSource;

        private DamageTimer(DamageSource damageSource, Player attacker) {
            this.damageSource = damageSource;
            this.attacker = attacker;
        }

        public static void addNewTimer(LivingEntity living, DamageSource damageSource, Player attacker) {
            DamageTimer timer = new DamageTimer(damageSource, attacker);
            timer.addSkillTimer(living, 8, 100, 1, true);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            performAttack(entity);
        }

        private void performAttack(LivingEntity entity) {
            if (entity.isAlive() && !entity.isRemoved()) {
                float damage = 40f + entity.random.nextInt(51);
                EntityUtil.clearHurtTime(entity, () ->  {
                    entity.hurt(damageSource, damage);
                    SoundUtil.playSound(entity, ModSounds.NOTHING_THERE_WEAPON_SPECIAL_ATTACK.get());
                });
            }
        }
    }
}