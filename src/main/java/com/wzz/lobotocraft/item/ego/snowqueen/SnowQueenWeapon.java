package com.wzz.lobotocraft.item.ego.snowqueen;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SnowQueenWeapon extends BaseEgoWeapon implements AiSpecialEgoWeapon {
    @Override public double aiSpecialAttackRange() { return 6.0D; }
    private static final UUID REACH_UUID = UUID.fromString("366d55e8-cdb7-48e7-bc95-d9a722c9faaf");
    public SnowQueenWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(6),
                MathUtil.toSpeedModifier(1.2f),
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
                            2.0D, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return originalModifiers;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public String weaponName() {
        return "snowqueen";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean fullyCharged = SnowQueenAttackPolicy.canApplyKissSpecial(
                player.getAttackStrengthScale(0.0F));
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        Random random = new Random();
        float damage = 6 + random.nextInt(7);
        target.hurt(DamageHelper.getDamage(player, "white"), damage);
        if (!(target instanceof Player) && fullyCharged) {
            int i = random.nextInt(101);
            if (i <= 10) {
                if (EgoArmorHelper.isFullEGO(player, "snowqueen"))
                    target.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 200));
            }
            target.getPersistentData().putInt("SnowQueenWeaponIce", target.getPersistentData().getInt("SnowQueenWeaponIce") + 1);
            if (target.getPersistentData().getInt("SnowQueenWeaponIce") >= 10) {
                if (!EgoArmorHelper.isFullEGO(player, "snowqueen"))
                    target.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 200));
                else applyKissEffect(target);
                target.getPersistentData().putInt("SnowQueenWeaponIce", 0);
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        triggerAttackAnimation(player, player.getItemInHand(hand));
        player.getCooldowns().addCooldown(this, 20);
        float damage = 6 + new Random().nextInt(7);
        for (LivingEntity living : EntityUtil.findAllEntitiesInLookDirection(player, 6, LivingEntity.class)) {
            living.hurt(DamageHelper.getDamage(player, "white"), damage);
            if (!(living instanceof Player)) {
                living.getPersistentData().putInt("SnowQueenWeaponIce", living.getPersistentData().getInt("SnowQueenWeaponIce") + 1);
                if (living.getPersistentData().getInt("SnowQueenWeaponIce") >= 10) {
                    if (!EgoArmorHelper.isFullEGO(player, "snowqueen"))
                        living.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 200));
                    else applyKissEffect(living);
                    living.getPersistentData().putInt("SnowQueenWeaponIce", 0);
                }
            }
        }
        return super.use(world, player, hand);
    }

    public void applyKissEffect(LivingEntity target) {
        MobEffectInstance existingEffect = target.getEffect(ModMobEffects.KISS.get());
        if (existingEffect != null) {
            // 已有效果，叠加层数（最高3层）
            int currentAmplifier = existingEffect.getAmplifier();
            int newAmplifier = Math.min(currentAmplifier + 1, 2); // 最高3层(amplifier=2)
            int remainingDuration = existingEffect.getDuration();
            SoundUtil.playSound(target.level, target, ModSounds.SNOWQUEEN_WEAPON_ICEBOUND.get());
            // 移除旧效果，添加新效果
            target.removeEffect(ModMobEffects.KISS.get());
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.KISS.get(),
                    remainingDuration + 100, // 刷新持续时间
                    newAmplifier
            ));
        } else {
            // 新效果，初始1层
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.KISS.get(),
                    200, // 10秒基础持续时间
                    0 // amplifier=0表示1层
            ));
        }
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.frost_splinter_weapon.summary",
                    ChatFormatting.GOLD);
            for (int index = 1; index <= 4; index++) {
                EgoTooltipUtil.blank(p_41423_);
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.frost_splinter_weapon.story." + index,
                        ChatFormatting.GRAY);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details",
                    ChatFormatting.GRAY);
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.frost_splinter_weapon.detail",
                    ChatFormatting.GOLD);
        }
    }
}
