package com.wzz.lobotocraft.item.ego.bigbadwolf;

import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.init.ModParticleTypes;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BigBadwolfWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 3.1;
    public BigBadwolfWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(12),
                MathUtil.toSpeedModifier(0.6f),
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
        float damage = 12 + new Random().nextInt(6);
        if (player.getHealth() < player.getMaxHealth() / 2) {
            damage = damage * 2;
        } else {
            SoundUtil.playSound(player.level, player, ModSounds.BIG_BADWOLF_WEAPON.get());
        }
        target.hurt(target.damageSources().playerAttack(player), damage);
        DotHelper.applyDot("big_badwolf", player, target, 3f, "red", 20, 100, 1);
        if (EgoArmorHelper.isFullEGO(player, "big_badwolf")) {
            SlowTimer.applySlowEffect(target);
        }
        return true;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity instanceof Player player && !player.level.isClientSide && player.getHealth() < player.getMaxHealth() / 2) {
            SoundUtil.playSound(player.level, player, ModSounds.BIG_BADWOLF_WEAPON.get());
            float damage = 24 + new Random().nextInt(6);
            for (LivingEntity target : EntityUtil.findAllEntitiesInLookDirection(player, 4, 3, LivingEntity.class)) {
                target.hurt(target.damageSources().playerAttack(player), damage);
                DotHelper.applyDot("big_badwolf", player, target, 3f, "red", 20, 100, 1);
                if (EgoArmorHelper.isFullEGO(player, "big_badwolf")) {
                    SlowTimer.applySlowEffect(target);
                }
            }
        }
        return super.onEntitySwing(stack, entity);
    }

    @Override
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 100;
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
            SoundUtil.playSound(level, player, ModSounds.BIG_BADWOLF_WEAPON.get());
            triggerAttackAnimation(player, stack);
            float damage = 15f;
            if (player.getHealth() < player.getMaxHealth() / 2) {
                damage = 30f;
            }
            for (LivingEntity target : EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE)) {
                DamageTimer.addNewTimer(target, DamageHelper.getDamage(player, "red"), damage, player);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (entity instanceof Player player && !level.isClientSide &&
                player.tickCount % 20 == 0 && isSelected && player.getHealth() < player.getMaxHealth() / 2 && player.isAlive()) {
            ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.DARK_BLUE_LIGHT.get(), 20, 0.1D);
        }
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 3);
        map.put("TemperanceLevel", 3);
        map.put("EmployeeLevel", 3);
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
        return "big_badwolf";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("※持有者攻击时会在一定时间持续造成红色伤害。如果持有者的生命值小于或等于50%，武器的伤害会额外增加。但是，持有者在该状态下攻击时会对其他玩家造成无差别伤害。")
                    .withStyle(ExtendedColor.ORANGE.toStyle()));
            p_41423_.add(Component.literal("※持有者攻击到目标时会为其附加一个每秒受到3点物理红色伤害的dot，持续5秒，不可叠加，每次造成伤害刷新持续时间。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※该武器右键可以发起一次特殊攻击，造成3x3范围的30点红色伤害。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※在持有者的生命值低于50%时，玩家周围会有深蓝色渲染特效环绕，武器造成的红色伤害提高100%，普通攻击命中会造成4x4的范围伤害，此时这把武器造成伤害将不分敌我。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            return;
        }
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7这件武器看起来就像那只邪恶巨狼的爪子。"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7这些爪子曾经撕裂，割破了无数生物的内脏。"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7爪子能够削开目标的血肉，让血液喷涌而出。"));
    }

    public static class DamageTimer extends TimerEntry<LivingEntity> {
        private final DamageSource damageSource;
        private final float damage;
        private final Player player;

        private DamageTimer(DamageSource damageSource, float damage, Player player) {
            this.damageSource = damageSource;
            this.damage = damage;
            this.player = player;
        }

        public static void addNewTimer(LivingEntity living, DamageSource damageSource, float damage, Player player) {
            DamageTimer timer = new DamageTimer(damageSource, damage, player);
            timer.addSkillTimer(living, 1200, 1000, 20, true);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            entity.getPersistentData().putBoolean("NotMove", true);
        }

        @Override
        public void onRunning(@NotNull LivingEntity entity) {
            if (getExecutions() == 4) {
                EntityUtil.clearHurtTime(entity, () -> entity.hurt(damageSource, damage));
            }
            if (getExecutions() == 15) {
                EntityUtil.clearHurtTime(entity, () -> entity.hurt(damageSource, damage));
            }
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            entity.getPersistentData().putBoolean("NotMove", false);
            DotHelper.applyDot("big_badwolf", player, entity, 3f, "red", 20, 100, 1);
        }
    }

    public static class SlowTimer extends TimerEntry<LivingEntity> {
        private static final UUID SLOW_UUID = UUID.fromString("c5db53a9-ffb8-4e4e-b171-c2ccb77d6244");
        private final double slowAmount; // -0.6 表示减少60%

        public SlowTimer(double slowAmount) {
            this.slowAmount = slowAmount;
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            applySlow(entity, true);
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            applySlow(entity, false);
        }

        private void applySlow(LivingEntity entity, boolean apply) {
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(SLOW_UUID);
                if (apply) {
                    AttributeModifier modifier = new AttributeModifier(
                            SLOW_UUID,
                            "slow_effect",
                            slowAmount,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    attribute.addTransientModifier(modifier);
                }
            }
        }

        public static void applySlowEffect(LivingEntity target) {
            int duration = 10 * 50;
            SlowTimer timer = new SlowTimer(-0.6);
            timer.addSkillTimer(target, 0, duration, 1, true);
        }
    }
}