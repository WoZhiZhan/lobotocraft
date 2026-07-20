package com.wzz.lobotocraft.item.ego.queen_bee;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QueenBeeWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 25.0;
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
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + getRemainingCooldown(player, stack) + " tick"), true);
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
            p_41423_.add(Component.literal("§7蜂之王国需要有更多的工蜂才能发展壮大，成就自己的江山。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7尽管王国会在历史上备受瞩目，可又有谁会记得为它做出奉献与牺牲的工蜂呢?"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7这杆枪不需要瞄准就可以击中它的目标，唯一需要的就是持有者的意念。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7它射出的子弹可以击中目不所及的敌人，甚至连历史都能够跨越。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※攻击命中目标时，为其施加”威胁“效果，一个维度内最多存在一个“威胁”。其他玩家对“威胁”造成伤害提高10%。“威胁”受到dot造成的伤害提高500%"));
        }
    }
}