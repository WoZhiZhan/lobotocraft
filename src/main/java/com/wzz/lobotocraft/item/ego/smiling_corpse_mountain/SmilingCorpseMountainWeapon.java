package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SmilingCorpseMountainWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 4.0;
    private static final int USE_COOLDOWN_TICKS = 40;
    public SmilingCorpseMountainWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(16),
                MathUtil.toSpeedModifier(1.5f),
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
        triggerAttackAnimation(player, stack);
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        float damage = 16;
        target.hurt(DamageHelper.getDamage(player, "black"), damage);
        SoundUtil.playSound(player, ModSounds.SMILING_CORPSE_MOUNTAIN_NORMAL_ATTACK.get());
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            stack.getOrCreateTag().putInt("UseTick", stack.getOrCreateTag().getInt("UseTick") - 1);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            player.displayClientMessage(Component.literal("§a冷却中,还剩:" + stack.getOrCreateTag().getInt("UseTick") + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        stack.getOrCreateTag().putInt("UseTick", USE_COOLDOWN_TICKS);
        if (!level.isClientSide) {
            triggerAttackAnimation(player, stack);
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                    @Override
                    public void onStart(@NotNull LivingEntity entity) {
                        SoundUtil.playSound(player, ModSounds.SMILING_CORPSE_MOUNTAIN_SPECIAL_ATTACK.get());
                    }

                    @Override
                    public void onEnd(@NotNull LivingEntity entity) {
                        entity.hurt(DamageHelper.getDamage(player, "black"), 32F);
                    }
                };
                timerEntry.addSkillTimer(target, 500, 310, 1);
            }
        }
        return super.use(level, player, hand);
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
            p_41423_.add(Component.literal("§6※这把武器每次普通攻击都会为对方施加1层腐败效果，每层会让该武器的特殊攻击伤害提高20%。"));
            p_41423_.add(Component.literal("§6※特殊攻击造成的黑色伤害翻倍。使用特殊攻击后，会清除掉对方身上所有的腐败效果。"));
        }
    }
}