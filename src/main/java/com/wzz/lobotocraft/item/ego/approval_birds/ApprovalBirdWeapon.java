package com.wzz.lobotocraft.item.ego.approval_birds;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalBirdWeapon extends BaseEgoWeapon implements AiSpecialEgoWeapon {
    @Override public double aiSpecialAttackRange() { return 4.0D; }
    public ApprovalBirdWeapon() {
        super(
                new ModTier.WeaponTier(),
                1,
                MathUtil.toSpeedModifier(0.9f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public String weaponName() {
        return "approval_bird";
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (stack.getOrCreateTag().getBoolean("isInAnimatable") && !level.isClientSide) {
            stack.getOrCreateTag().putInt("AnimTime", stack.getOrCreateTag().getInt("AnimTime") + 1);
            if (stack.getOrCreateTag().getInt("AnimTime") >= 300) {
                stack.getOrCreateTag().remove("isInAnimatable");
                stack.getOrCreateTag().remove("AnimTime");
            }
        }
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 5);
        map.put("EmployeeLevel", 5);
        return map;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (stack.getOrCreateTag().getBoolean("isInAnimatable")) {
            return false;
        }
        if (!canUseItem(player))
            return false;
        if (entity instanceof LivingEntity livingEntity) {
            float attackMultiplier = BaseEgoWeapon.currentLeftClickDamageMultiplier(player);
            player.playSound(ModSounds.APPROVAL_BIRD_WEAPON_ATTACK.get());
            if (!player.level.isClientSide) {
                player.getServer().execute(() -> {
                    float f = player.random.nextFloat();
                    if (f <= 0.4f && stack.getOrCreateTag().getInt("UseTick") > 0) {
                        player.displayClientMessage(Component.translatable(
                                "message.lobotocraft.ego.approval_bird.cooldown_ready"), false);
                        player.playSound(SoundEvents.PLAYER_LEVELUP);
                        stack.getOrCreateTag().putInt("UseTick", 0);
                    }
                    TimerEntry<Player> timerEntry = new TimerEntry<>() {
                        @Override
                        public void onEnd(@NotNull Player living) {
                            EntityUtil.clearHurtTime(livingEntity, () -> {
                                livingEntity.hurt(DamageHelper.getDamage(player, "blue"), attackMultiplier * (1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f)));
                                EntityUtil.clearHurtTime(livingEntity, () -> {
                                    livingEntity.hurt(DamageHelper.getDamage(player, "blue"), attackMultiplier * (1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f)));
                                    EntityUtil.clearHurtTime(livingEntity, () -> {
                                        livingEntity.hurt(DamageHelper.getDamage(player, "blue"), attackMultiplier * (1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f)));
                                        EntityUtil.clearHurtTime(livingEntity, () -> {
                                            livingEntity.hurt(DamageHelper.getDamage(player, "blue"), attackMultiplier * (1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f)));
                                            EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                                    attackMultiplier * (1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.04f, 3f))));
                                        });
                                    });
                                });
                            });
                        }
                    };
                    timerEntry.addSkillTimer(player, 0, 200, 1, true);
                });
            }
        }
        return true;
    }

    @Override
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 1200;
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
        if (!canUseItem(player))
            return InteractionResultHolder.pass(stack);
        if (!stack.getOrCreateTag().getBoolean("isInAnimatable")) {
            stack.getOrCreateTag().putBoolean("isInAnimatable", true);
            stack.getOrCreateTag().remove("AnimTime");
            startCooldown(player, stack);
            triggerAttackAnimation(player, stack);
            if (!level.isClientSide) {
                player.getServer().execute(() -> {
                    TimerEntry<Player> timerEntry = new TimerEntry<>() {
                        @Override
                        public void onRunning(@NotNull Player living) {
                            int tick = this.getExecutions();
                            if (tick == 26) {
                                SoundUtil.playSound(living.level, living, ModSounds.APPROVAL_BIRD_WEAPON_SPECIAL_1.get());
                            }
                            if (tick == 30) {
                                LivingEntity livingEntity = EntityUtil.findEntityInLookDirection(player, 4, 3, LivingEntity.class);
                                if (livingEntity != null) {
                                    EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                            2 + player.random.nextInt(3) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.07f, 0.1f, 24f)));
                                    ParticleUtil.spawnParticles(livingEntity, ParticleUtil.getDustParticle(1,0,0,0.8f), 10, 0.1d);
                                }
                            }
                            if (tick == 61) {
                                SoundUtil.playSound(living.level, living, ModSounds.APPROVAL_BIRD_WEAPON_SPECIAL_2.get());
                                LivingEntity livingEntity = EntityUtil.findEntityInLookDirection(player, 4, 3, LivingEntity.class);
                                if (livingEntity != null) {
                                    EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                            7 + player.random.nextInt(3) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.07f, 0.1f, 24f)));
                                    ParticleUtil.spawnParticles(livingEntity, ParticleUtil.getDustParticle(1,0,0,0.8f), 10, 0.1d);
                                }
                            }
                            if (tick == 70) {
                                SoundUtil.playSound(living.level, living, ModSounds.APPROVAL_BIRD_WEAPON_SPECIAL_3.get());
                                LivingEntity livingEntity = EntityUtil.findEntityInLookDirection(player, 4, 3, LivingEntity.class);
                                if (livingEntity != null) {
                                    EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                            7 + player.random.nextInt(3) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.07f, 0.1f, 24f)));
                                    ParticleUtil.spawnParticles(livingEntity, ParticleUtil.getDustParticle(1,0,0,0.8f), 10, 0.1d);
                                }
                            }
                            if (tick == 85) {
                                SoundUtil.playSound(living.level, living, ModSounds.APPROVAL_BIRD_WEAPON_SPECIAL_4.get());
                                LivingEntity livingEntity = EntityUtil.findEntityInLookDirection(player, 4, 3, LivingEntity.class);
                                if (livingEntity != null) {
                                    EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                            2 + player.random.nextInt(3) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.07f, 0.1f, 24f)));
                                    ParticleUtil.spawnParticles(livingEntity, ParticleUtil.getDustParticle(1,0,0,0.8f), 10, 0.1d);
                                }
                            }
                            if (tick == 110) {
                                SoundUtil.playSound(living.level, living, ModSounds.APPROVAL_BIRD_WEAPON_SPECIAL_4.get());
                                LivingEntity livingEntity = EntityUtil.findEntityInLookDirection(player, 4, 3, LivingEntity.class);
                                if (livingEntity != null) {
                                    EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                            2 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.07f, 0.1f, 24f)));
                                    ParticleUtil.spawnParticles(livingEntity, ParticleUtil.getDustParticle(1,0,0,1f), 20, 0.1d);
                                }
                            }
                        }

                        @Override
                        public void onEnd(@NotNull Player living) {
                            stack.getOrCreateTag().putBoolean("isInAnimatable", false);

                        }
                    };
                    timerEntry.addSkillTimer(player, 0, 6000, 20);
                });
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    protected String getAttackName() {
        return "animation.model.1";
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        if (ClientInputUtil.isShiftPressed()) {
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.approval_bird_weapon.detail.1");
            EgoTooltipUtil.blank(components);
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.approval_bird_weapon.detail.2");
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.approval_bird_weapon.detail.3");
            return;
        }
        for (int index = 1; index <= 5; index++) {
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.approval_bird_weapon.story." + index);
        }
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.common.shift_details");
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.approval_bird_weapon.damage");
    }
}
