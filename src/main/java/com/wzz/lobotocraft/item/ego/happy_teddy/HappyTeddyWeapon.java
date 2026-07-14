package com.wzz.lobotocraft.item.ego.happy_teddy;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

public class HappyTeddyWeapon extends BaseEgoWeapon {
    private static final UUID REACH_UUID = UUID.fromString("627cac40-6b38-49c2-96ec-5ce700c979e9");
    public HappyTeddyWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(7),
                MathUtil.toSpeedModifier(0.8f),
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
        float damage = 7;
        CompoundTag compoundTag = target.getPersistentData();
        EntityUtil.clearHurtTime(target, () -> {
            compoundTag.putFloat("HappyTeddyDamage", compoundTag.getFloat("HappyTeddyDamage") + damage);
                    target.hurt(player.damageSources().playerAttack(player), damage);
                    EntityUtil.clearHurtTime(target, () -> {
                        compoundTag.putFloat("HappyTeddyDamage", compoundTag.getFloat("HappyTeddyDamage") + damage);
                                target.hurt(player.damageSources().playerAttack(player), damage);
                                if (compoundTag.getFloat("HappyTeddyDamage") >= 40) {
                                    compoundTag.putFloat("HappyTeddyDamage", 0);
                                    TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                                        @Override
                                        public void onStart(@NotNull LivingEntity entity) {
                                            entity.getPersistentData().putBoolean("NotMove", true);
                                            if (entity instanceof Mob mob) {
                                                mob.setNoAi(true);
                                            }
                                        }

                                        @Override
                                        public void onRunning(@NotNull LivingEntity entity) {
                                            ParticleUtil.spawnParticlesAroundEntity(entity, ParticleTypes.CRIT,
                                                    15, 0.1D, 0, 1, 0);
                                        }

                                        @Override
                                        public void onEnd(@NotNull LivingEntity entity) {
                                            entity.getPersistentData().remove("NotMove");
                                            if (entity instanceof Mob mob) {
                                                mob.setNoAi(false);
                                            }
                                        }
                                    };
                                    timerEntry.addSkillTimer(target, 0, 1000, 10, true);
                                }
                            }
                    );
                }
        );
        if (EgoArmorHelper.isFullEGO(player, "happy_teddy")) {
            int i = new Random().nextInt(101);
            if (i <= 20) {
                EntityUtil.clearHurtTime(target, () ->
                        target.hurt(DamageHelper.getDamage(player, "white"), damage)
                );
            }
        }
        return true;
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 2);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "happy_teddy";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7柔软的棉花填充着这只可爱的泰迪熊。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7它的身体因为岁月而变得破烂不堪，就如同人们早已丢失的童真一样。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7可它还是很可爱！如果把它作为礼物送给孩子们的话，他们一定会兴奋地抱住它。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7它的身体十分脆弱，很容易被撕开，所以对待它需格外小心。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7一些富有同情心的员工对此感到十分痛心，并建议修复它。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7但是对一个异想体的修理结果是无法预测的，因此这些建议都被回绝了。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7不要被它毛茸茸的可爱外表所迷惑，它的力量远超我们的想象。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※每次攻击会造成2段伤害。"));
        }
    }
}