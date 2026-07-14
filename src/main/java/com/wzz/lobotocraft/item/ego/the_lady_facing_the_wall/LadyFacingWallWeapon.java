package com.wzz.lobotocraft.item.ego.the_lady_facing_the_wall;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class LadyFacingWallWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 10.0;
    private static final int USE_COOLDOWN_TICKS = 12;
    public LadyFacingWallWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(2),
                MathUtil.toSpeedModifier(0.6f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "the_lady_facing_the_wall";
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
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.CRIT, 40, 0.1d, ATTACK_RANGE);
            SoundUtil.playSound(level, player, ModSounds.PUNISHING_BIRD_WEAPON_ATTACK.get());
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                float damage = 2f + new Random().nextInt(2);
                DamageSource damageSource = DamageHelper.getDamage(player, "white");
                target.hurt(damageSource, damage);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7即便该异想体的核心已经变成了E.G.O的形式，那种强烈的孤独感也仍旧存在于这把武器上。"));
        p_41423_.add(Component.literal("§7它射出的子弹并不会穿透敌人的骨头，反而会留下永久的，孤独的空白。"));
        p_41423_.add(Component.literal("§7这把手枪在被制造出来时便已锈迹斑斑了。"));
    }
}