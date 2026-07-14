package com.wzz.lobotocraft.item.ego.red_shoes;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class RedShoesWeapon extends BaseEgoWeapon {
    private static final UUID REACH_UUID = UUID.fromString("0c303a48-069b-4727-a9ae-b3cac84b6c1a");
    public RedShoesWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(6),
                MathUtil.toSpeedModifier(1f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> originalModifiers = super.getAttributeModifiers(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(originalModifiers);
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, weaponName() + "_reach",
                            -1.0D, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return originalModifiers;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "red_shoes";
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
        if (player instanceof ServerPlayer serverPlayer) {
            AtomicReference<Float> damage = new AtomicReference<>((float) 6);
            if (EgoArmorHelper.isFullEGO(player, "red_shoes")) {
                serverPlayer.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                    float ex = stats.getTemperanceLevel();
                    damage.updateAndGet(v -> v + ex);
                });
            }
            target.hurt(DamageHelper.getDamage(player, "red"), damage.get());
        }
        return true;
    }


    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§6※如果持有者的自律等级低于3级，每次攻击都会丧失等同于最大精神值4%的精神值。自律等级越高伤害越高。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7甚至在女孩的双脚被砍断后，舞鞋依旧凭借着难以置信的执念，带着残肢进入了森林。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7大多数员工都熟悉这双舞鞋曾引发的悲惨往事。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7持有者应当牢记，即便是这把质地轻盈的红斧头，也曾因错误的抉择而害死了很多人。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7持有者的信念越是坚定，红斧头就越是强大。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7然而，若被贪婪蒙蔽了双眼，就再也无法从虚妄中醒来。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※玩家自律等级每高1级就提高1点伤害，低于3级时，每次攻击会扣除自身4%的精神值。"));
        }
    }
}