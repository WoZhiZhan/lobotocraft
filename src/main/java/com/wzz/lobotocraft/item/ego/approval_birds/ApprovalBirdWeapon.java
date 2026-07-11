package com.wzz.lobotocraft.item.ego.approval_birds;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalBirdWeapon extends BaseEgoWeapon {
    public ApprovalBirdWeapon() {
        super(
                new Tier(),
                1,
                -2.0f,
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
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            stack.getOrCreateTag().putInt("UseTick", stack.getOrCreateTag().getInt("UseTick") - 1);
        }
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
        if (entity instanceof LivingEntity livingEntity && player.getAttackStrengthScale(0.0f) == 1.0f) {
            player.playSound(ModSounds.APPROVAL_BIRD_WEAPON_ATTACK.get());
            if (!player.level.isClientSide) {
                player.getServer().execute(() -> {
                    float f = player.random.nextFloat();
                    if (f <= 0.4f && stack.getOrCreateTag().getInt("UseTick") > 0) {
                        player.displayClientMessage(Component.literal("§a特殊攻击的冷却提前解除了！"), false);
                        player.playSound(SoundEvents.PLAYER_LEVELUP);
                        stack.getOrCreateTag().putInt("UseTick", 0);
                    }
                    TimerEntry<Player> timerEntry = new TimerEntry<>() {
                        @Override
                        public void onEnd(@NotNull Player living) {
                            EntityUtil.clearHurtTime(livingEntity, () -> {
                                livingEntity.hurt(DamageHelper.getDamage(player, "blue"), 1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f));
                                EntityUtil.clearHurtTime(livingEntity, () -> {
                                    livingEntity.hurt(DamageHelper.getDamage(player, "blue"), 1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f));
                                    EntityUtil.clearHurtTime(livingEntity, () -> {
                                        livingEntity.hurt(DamageHelper.getDamage(player, "blue"), 1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f));
                                        EntityUtil.clearHurtTime(livingEntity, () -> {
                                            livingEntity.hurt(DamageHelper.getDamage(player, "blue"), 1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.03f, 3f));
                                            EntityUtil.clearHurtTime(livingEntity, () -> livingEntity.hurt(DamageHelper.getDamage(player, "blue"),
                                                    1 + player.random.nextInt(2) + 1 + EntityUtil.addMaxHealthPercentageDamage(livingEntity, 0.02f, 0.04f, 3f)));
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            player.displayClientMessage(Component.literal("§a冷却中,还剩:" + stack.getOrCreateTag().getInt("UseTick") + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        if (!canUseItem(player))
            return InteractionResultHolder.pass(stack);
        if (!stack.getOrCreateTag().getBoolean("isInAnimatable")) {
            stack.getOrCreateTag().putBoolean("isInAnimatable", true);
            stack.getOrCreateTag().remove("AnimTime");
            stack.getOrCreateTag().putInt("UseTick", 1200);
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
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            components.add(Component.literal("§a冷却中：" + stack.getOrCreateTag().getInt("UseTick")));
        }
        if (ClientInputUtil.isShiftPressed()) {
            components.add(Component.literal("§6※这把武器每次满蓄力攻击时会额外造成4段蓝色伤害（由这把武器造成的灵魂伤害在基础数值上额外附带对方生命值上限2%-3%的蓝色伤害，最高3点）。"));
            components.add(Component.literal(""));
            components.add(Component.literal("§6※这把武器右键时使用特殊攻击，持有者对目标进行一次劈砍后接两次戳刺和两次劈砍，造成2次7-10点蓝色伤害和3次2-4点蓝色伤害（由这把武器特殊攻击造成的灵魂伤害在基础数值上额外附带对方生命值上限7%-10%的蓝色伤害，最高24点）。"));
            components.add(Component.literal("§7特殊攻击使用后武器进入60s冷却，可以左键无法右键，每次满蓄力左键攻击有40%的概率解除冷却"));
            return;
        }
        components.add(Component.literal("§7这把武器象征着审判鸟的公平制裁，这也意味着它需要去权衡全部的罪恶。"));
        components.add(Component.literal("§7当这把武器穿过敌人的身体时，它能一并抹去敌人身上所携的罪恶之痕。"));
        components.add(Component.literal("§7只有公司中最公正无私的人才能拿起这把E.G.O武器。"));
        components.add(Component.literal("§7不要试着移去缠在这把武器上的绷带，它掩盖着那些属于过去的，不应被人所了解的悲哀记忆。"));
        components.add(Component.literal("§7这把武器也在寻求着，渴望着为所有人带来和平与公正，就如同审判鸟的初衷一样..."));
        components.add(Component.literal("§7按住<Shift>查看详情"));
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 2.0F;
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