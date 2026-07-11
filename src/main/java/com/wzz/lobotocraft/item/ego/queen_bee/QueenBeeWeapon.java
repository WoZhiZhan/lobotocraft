package com.wzz.lobotocraft.item.ego.queen_bee;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QueenBeeWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 25.0;
    private static final int USE_COOLDOWN_TICKS = 20;
    private static final UUID REACH_UUID = UUID.fromString("fe190413-499b-43c1-92e9-59f4eb546434");
    public QueenBeeWeapon() {
        super(
                new Tier(),
                MathUtil.toDamageModifier(8),
                MathUtil.toSpeedModifier(1f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 3);
        return map;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> originalModifiers = super.getAttributeModifiers(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(originalModifiers);
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, weaponName() + "_reach",
                            22.0D, AttributeModifier.Operation.ADDITION));
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
        return "queen_bee";
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
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.CRIT, 40, 0.1d, 25);
            SoundUtil.playSound(level, player, ModSounds.QUEEN_BEE_WEAPON.get());
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                DamageSource damageSource = DamageHelper.getDamage(player, "red");
                target.hurt(damageSource, 8f);
                boolean otherHas = false;
                for (LivingEntity living : EntityUtil.findAllEntitiesWithDimension(player, player.level.dimension)) {
                    if (living.isAlive() && living.hasEffect(ModEffects.MENACE.get())) {
                        otherHas = true;
                        break;
                    }
                }
                if (!otherHas) {
                    target.addEffect(new MobEffectInstance(ModEffects.MENACE.get(), 400, 1));
                }
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7蜂之王国需要有更多的工蜂才能发展壮大，成就自己的江山。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7尽管王国会在历史上备受瞩目，可又有谁会记得为它做出奉献与牺牲的工蜂呢?"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7这杆枪不需要瞄准就可以击中它的目标，唯一需要的就是持有者的意念。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7它射出的子弹可以击中目不所及的敌人，甚至连历史都能够跨越。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※攻击命中目标时，为其施加”威胁“效果（身边有黄色粒子特效环绕），一个维度内最多存在一个“威胁”。其他玩家对“威胁”造成伤害提高10%。“威胁”受到dot造成的伤害提高500%（除开这把武器目前会造成dot的仅有“因乐颠狂”）"));
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