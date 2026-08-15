package com.wzz.lobotocraft.item.ego.nothing_there;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.item.ego.base.EgoDamageRecoveryPolicy;
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

public class NothingThereWeapon extends BaseEgoWeapon implements AiSpecialEgoWeapon {
    private static final double ATTACK_RANGE = 4.0;
    private static final float NORMAL_HIT_COOLDOWN_RESET_CHANCE = 0.15F;
    private static final float ATTACK_SOUND_VOLUME = 0.6F;

    public NothingThereWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(10),
                MathUtil.toSpeedModifier(1.2f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public double aiSpecialAttackRange() {
        return ATTACK_RANGE;
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
        SoundUtil.playSound(player.level, player, ModSounds.NOTHING_THERE_WEAPON_NORMAL_ATTACK.get(),
                ATTACK_SOUND_VOLUME);
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        float damage = 11 + player.random.nextInt(4);
        float healthBefore = target.getHealth();
        boolean hit = target.hurt(DamageHelper.getDamage(player, "red"), damage);
        float damageDealt = EgoDamageRecoveryPolicy.actualHealthLoss(healthBefore, target.getHealth());
        if (!player.level().isClientSide && hit && isOnCooldown(player, stack)
                && player.random.nextFloat() < NORMAL_HIT_COOLDOWN_RESET_CHANCE) {
            resetCooldown(stack);
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.ego.nothing_there.cooldown_ready"), true);
        }
        if (damageDealt > 0.0F) player.heal(isFullEGO ? damageDealt : damageDealt * 0.5F);
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
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.ego.cooldown_ticks",
                    getRemainingCooldown(player, stack)), true);
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
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_weapon.summary");
            for (int index = 1; index <= 3; index++) {
                EgoTooltipUtil.blank(p_41423_);
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_weapon.story." + index);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details");
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_weapon.detail.1");
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_weapon.detail.2");
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
                    SoundUtil.playSound(entity.level, entity, ModSounds.NOTHING_THERE_WEAPON_SPECIAL_ATTACK.get(),
                            ATTACK_SOUND_VOLUME);
                });
            }
        }
    }
}
