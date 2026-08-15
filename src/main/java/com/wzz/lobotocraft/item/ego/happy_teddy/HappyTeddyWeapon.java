package com.wzz.lobotocraft.item.ego.happy_teddy;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
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
    private static final String MOVEMENT_LOCK = "happy_teddy_weapon";
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
        DamageSource source = DamageHelper.getDamage(player, "white");
        CompoundTag compoundTag = target.getPersistentData();
        EntityUtil.clearHurtTime(target, () -> {
            compoundTag.putFloat("HappyTeddyDamage", compoundTag.getFloat("HappyTeddyDamage") + damage);
                    target.hurt(source, damage);
                    EntityUtil.clearHurtTime(target, () -> {
                        compoundTag.putFloat("HappyTeddyDamage", compoundTag.getFloat("HappyTeddyDamage") + damage);
                                target.hurt(source, damage);
                                if (compoundTag.getFloat("HappyTeddyDamage") >= 40) {
                                    compoundTag.putFloat("HappyTeddyDamage", 0);
                                    TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                                        @Override
                                        public void onStart(@NotNull LivingEntity entity) {
                                            MovementLockUtil.acquire(entity, MOVEMENT_LOCK);
                                            if (entity instanceof Mob mob) {
                                                MobAiLockUtil.acquire(mob, MOVEMENT_LOCK);
                                            }
                                        }

                                        @Override
                                        public void onRunning(@NotNull LivingEntity entity) {
                                            ParticleUtil.spawnParticlesAroundEntity(entity, ParticleTypes.CRIT,
                                                    15, 0.1D, 0, 1, 0);
                                        }

                                        @Override
                                        public void onEnd(@NotNull LivingEntity entity) {
                                            clearStun(entity);
                                        }

                                        @Override
                                        public void onCancelled(@NotNull LivingEntity entity,
                                                @NotNull CancellationReason reason) {
                                            clearStun(entity);
                                        }

                                        private void clearStun(LivingEntity entity) {
                                            MovementLockUtil.release(entity, MOVEMENT_LOCK);
                                            if (entity instanceof Mob mob) {
                                                MobAiLockUtil.release(mob, MOVEMENT_LOCK);
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
            for (int index = 1; index <= 7; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.happy_teddy_weapon.story." + index);
                if (index < 7) EgoTooltipUtil.blank(p_41423_);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details");
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.happy_teddy_weapon.detail");
        }
    }
}
