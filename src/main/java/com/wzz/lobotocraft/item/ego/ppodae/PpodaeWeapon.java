package com.wzz.lobotocraft.item.ego.ppodae;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PpodaeWeapon extends BaseEgoWeapon {
    private static final UUID REACH_UUID = UUID.fromString("a1b2c3d4-0003-0003-0003-000000000003");
    public PpodaeWeapon() {
        super(
                new Tier(),
                MathUtil.toDamageModifier(5),
                MathUtil.toSpeedModifier(1),
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
                    new AttributeModifier(REACH_UUID, "ppodae_reach",
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
        return "ppodae";
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
        float secondHit = 5;
        EntityUtil.clearHurtTime(target, () -> {
                    target.hurt(player.damageSources().playerAttack(player), secondHit);
                    EntityUtil.clearHurtTime(target, () ->
                            target.hurt(player.damageSources().playerAttack(player), secondHit)
                    );
                }
        );
        return true;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7我想，从有着可爱外表的异想体中提取出来的武器应该不会很厉害吧？"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7好吧，我错了，这只爪子不仅结实耐用，还很可爱！！！"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7咳咳，如果你过度依赖这把武器，你会意识到寄托其中的残暴野性正在你的体内慢慢苏醒。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7但是但是...这些果冻状的爪爪垫摸起来真是让人欲罢不能啊，简直爽到~"));
        } else {
            p_41423_.add(Component.literal("§6※这把武器在攻击时会造成两段伤害。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        }
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 3.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}