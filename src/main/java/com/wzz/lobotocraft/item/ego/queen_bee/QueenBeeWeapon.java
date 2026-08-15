package com.wzz.lobotocraft.item.ego.queen_bee;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.item.ego.base.AiRangedEgoWeapon;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QueenBeeWeapon extends BaseEgoWeapon implements AiRangedEgoWeapon {
    private static final double ATTACK_RANGE = 25.0;
    @Override public double aiAttackRange() { return ATTACK_RANGE; }
    @Override public double aiMinSpacing() { return 8.0D; }
    @Override public double aiMaxSpacing() { return 22.0D; }
    @Override public double aiPreferredSpacing() { return 15.0D; }
    private static final UUID REACH_UUID = UUID.fromString("fe190413-499b-43c1-92e9-59f4eb546434");
    public QueenBeeWeapon() {
        super(
                new ModTier.WeaponTier(),
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
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 20;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isOnCooldown(player, stack)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.ego.cooldown_ticks",
                    getRemainingCooldown(player, stack)), true);
            return InteractionResultHolder.pass(stack);
        }
        startCooldown(player, stack);
        if (!level.isClientSide) {
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.CRIT, 40, 0.1d, 25);
            SoundUtil.playSound(level, player, ModSounds.QUEEN_BEE_WEAPON.get());
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                DamageSource damageSource = DamageHelper.getDamage(player, "red");
                target.hurt(damageSource, 8f);
                boolean otherHas = false;
                for (LivingEntity living : EntityUtil.findAllEntitiesWithDimension(player, player.level.dimension)) {
                    if (living.isAlive() && living.hasEffect(ModMobEffects.MENACE.get())) {
                        otherHas = true;
                        break;
                    }
                }
                if (!otherHas) {
                    target.addEffect(new MobEffectInstance(ModMobEffects.MENACE.get(), 400, 0));
                }
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 4; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.queen_bee_weapon.story." + index);
                if (index < 4) EgoTooltipUtil.blank(p_41423_);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details");
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.queen_bee_weapon.detail");
        }
    }
}
