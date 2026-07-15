package com.wzz.lobotocraft.item.ego.army_in_black;

import com.wzz.lobotocraft.event.SheepskinSetHandler;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;

public class ArmyInBlackWeapon extends BaseEgoWeapon {

    private static final double ATTACK_RANGE = 35;
    private static final int USE_COOLDOWN_TICKS = 6 * 20;
    private static final float HEALTH_THRESHOLD = 0.5f;
    private static final float WHITE_DAMAGE_BONUS = 4.0f;
    private static final float BLACK_DAMAGE_BONUS = 6.0f;
    private static final int SPLASH_RADIUS = 5;
    private static final int MAX_SPLASH_COUNT = 6;

    public ArmyInBlackWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(7),
                MathUtil.toSpeedModifier(0.9f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PrudenceLevel", 5);
        map.put("EmployeeLevel", 5);
        return map;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(stack);
        }

        int cooldown = stack.getOrCreateTag().getInt("UseTick");
        if (cooldown > 0) {
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + cooldown + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        stack.getOrCreateTag().putInt("UseTick",
                SheepskinSetHandler.getGunCooldown(player, USE_COOLDOWN_TICKS));
        if (!level.isClientSide) {
            SoundUtil.playSound(level, player, ModSounds.ARMY_IN_BLACK_SHOOT.get());
            triggerAnimation(player, stack, "2");
            fire(level, player);
        }
        return super.use(level, player, hand);
    }

    /** 开火逻辑 */
    private void fire(Level level, Player player) {
        boolean isAboveHalf = player.getHealth() > player.getMaxHealth() * HEALTH_THRESHOLD;

        if (isAboveHalf) {
            fireWhiteMode(level, player);
        } else {
            fireBlackMode(level, player);
        }
    }

    // ==================== 白色模式（生命值 > 50%） ====================
    private void fireWhiteMode(Level level, Player player) {
        ParticleUtil.spawnLineParticles(level, player, ModParticleTypes.ARMY_IN_BLACK_NORMAL_SHOOT.get(), 60, 0.05f, 35D);
        // 查找最近的一个目标
        List<LivingEntity> targets = EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE);
        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(0);
        player.playSound(ModSounds.ARMY_IN_BLACK_NORMAL_HIT.get(), 1.0f, 1.0f);
        float damage = 24 * WHITE_DAMAGE_BONUS;
        DamageSource source = DamageHelper.getDamage(player, "white");
        EntityUtil.clearHurtTime(target, () -> {
            ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.ARMY_IN_BLACK_EXPLODE.get(), 5, 0.1f);
            if (EgoArmorHelper.isFullEGO(player, "army_in_black")) {
                player.heal(10f);
                MentalValueUtil.addMentalValue(player, 10f);
            }
            target.hurt(source, damage);
        });
    }

    // ==================== 黑色模式（生命值 < 50%） ====================
    private void fireBlackMode(Level level, Player player) {
        ParticleUtil.spawnLineParticles(level, player, ModParticleTypes.ARMY_IN_BLACK_HALF_SHOOT.get(), 60, 0.05f, 35D);
        List<LivingEntity> targets = EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE);
        if (targets.isEmpty()) return;

        LivingEntity primaryTarget = targets.get(0);
        float damage = 24 * BLACK_DAMAGE_BONUS;
        player.playSound(ModSounds.ARMY_IN_BLACK_HALF_HIT.get(), 1.0f, 1.0f);
        DamageSource source = DamageHelper.getDamage(player, "black");
        EntityUtil.clearHurtTime(primaryTarget, () -> {
            ParticleUtil.spawnParticlesAroundEntity(primaryTarget, ModParticleTypes.ARMY_IN_BLACK_SPUTTERING.get(), 5, 0.2f);
            primaryTarget.hurt(source, damage);
        });
        SplashTimer.addNewTimer(primaryTarget, player, damage, SPLASH_RADIUS, MAX_SPLASH_COUNT);
    }

    public static class SplashTimer extends TimerEntry<LivingEntity> {
        private static final float SPLASH_RESTORE = 3.0f;
        private final Player attacker;
        private final float damage;
        private final int radius;
        private final int maxSplashCount;
        private int splashCount = 0;
        private LivingEntity lastTarget;

        private SplashTimer(Player attacker, float damage, int radius, int maxSplashCount) {
            this.attacker = attacker;
            this.damage = damage;
            this.radius = radius;
            this.maxSplashCount = maxSplashCount;
            this.setRequireMainThread(true);
        }

        public static void addNewTimer(LivingEntity target, Player attacker, float damage, int radius, int maxSplashCount) {
            SplashTimer timer = new SplashTimer(attacker, damage, radius, maxSplashCount);
            timer.addSkillTimer(target, 10, 10 * maxSplashCount, 2, true);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            performSplash(entity);
            splashCount = 1;
        }

        @Override
        public void onRunning(@NotNull LivingEntity entity) {
            splashCount++;
            if (splashCount > maxSplashCount) {
                return;
            }
            performSplash(entity);
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            lastTarget = null;
        }

        private void performSplash(LivingEntity center) {
            if (center == null || !center.isAlive() || center.isRemoved()) return;
            if (attacker == null || !attacker.isAlive()) return;

            Level level = center.level();
            if (level.isClientSide()) return;

            List<LivingEntity> targets = EntityUtil.findEntitiesAround(center, 2, radius, LivingEntity.class);
            targets.remove(center);
            targets.remove(attacker);

            if (!targets.isEmpty()) {
                LivingEntity target = targets.get(0);
                target.playSound(ModSounds.ARMY_IN_BLACK_SPUTTERING.get(), 1.0f, 1.0f);
                DamageSource source = target.damageSources().playerAttack(attacker);
                EntityUtil.clearHurtTime(target, () -> target.hurt(source, damage));
                lastTarget = target;
                ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.ARMY_IN_BLACK_SPUTTERING.get(), 5, 0.2f);
                onSplashHit();
            } else if (lastTarget != null && lastTarget.isAlive()) {
                DamageSource source = lastTarget.damageSources().playerAttack(attacker);
                lastTarget.playSound(ModSounds.ARMY_IN_BLACK_SPUTTERING.get(), 1.0f, 1.0f);
                EntityUtil.clearHurtTime(lastTarget, () -> lastTarget.hurt(source, damage));
                ParticleUtil.spawnParticlesAroundEntity(lastTarget, ModParticleTypes.ARMY_IN_BLACK_SPUTTERING.get(), 5, 0.2f);
                onSplashHit();
            } else {
                List<LivingEntity> allTargets = EntityUtil.findEntitiesAround(center, 2, radius, LivingEntity.class);
                allTargets.remove(center);
                allTargets.remove(attacker);
                if (!allTargets.isEmpty()) {
                    LivingEntity newTarget = allTargets.get(0);
                    newTarget.playSound(ModSounds.ARMY_IN_BLACK_SPUTTERING.get(), 1.0f, 1.0f);
                    DamageSource source = newTarget.damageSources().playerAttack(attacker);
                    EntityUtil.clearHurtTime(newTarget, () -> newTarget.hurt(source, damage));
                    lastTarget = newTarget;
                    ParticleUtil.spawnParticlesAroundEntity(lastTarget, ModParticleTypes.ARMY_IN_BLACK_SPUTTERING.get(), 5, 0.2f);
                    onSplashHit();
                }
            }
        }

        private void onSplashHit() {
            if (attacker == null || !attacker.isAlive()) return;
            if (!EgoArmorHelper.isFullEGO(attacker, "army_in_black")) return;
            if (attacker.getHealth() >= attacker.getMaxHealth() * HEALTH_THRESHOLD) return;
            attacker.heal(SPLASH_RESTORE);
            MentalValueUtil.addMentalValue(attacker, SPLASH_RESTORE);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BaseEgoWeapon> controller =
                new AnimationController<>(this, "controller", 0, state -> {
                    AnimationController<BaseEgoWeapon> ctrl = state.getController();
                    if (ctrl.getAnimationState() == AnimationController.State.STOPPED) {
                        ctrl.setAnimation(RawAnimation.begin().thenLoop("1"));
                    }
                    return PlayState.CONTINUE;
                });
        controller.triggerableAnim("1", RawAnimation.begin().thenLoop("1"));
        controller.triggerableAnim("2", RawAnimation.begin()
                .thenPlay("2")
                .thenPlay("3"));
        controller.triggerableAnim("3", RawAnimation.begin().thenLoop("3"));
        controllers.add(controller);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        int cooldown = stack.getOrCreateTag().getInt("UseTick");
        if (cooldown > 0) {
            stack.getOrCreateTag().putInt("UseTick", cooldown - 1);
        }
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    protected boolean autoRegisterAttackAnim() {
        return false;
    }

    @Override
    public String weaponName() {
        return "army_in_black";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7粉红色象征着温暖与爱。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7但这把粉红涂装的枪真的能够代表爱吗？"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7伤害他人的工具又该如何传递爱与和平？"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※当玩家生命值大于50%时，该武器射出的子弹会造成白色伤害，对单个目标造成的伤害提高300%。"));
            p_41423_.add(Component.literal("§6※当玩家生命值小于50%时，武器射出的子弹会造成黑色伤害，对多个目标造成溅射伤害，并提高伤害500%。"));
            p_41423_.add(Component.literal("§6※每次攻击会造成2段伤害。"));
        }
    }
}