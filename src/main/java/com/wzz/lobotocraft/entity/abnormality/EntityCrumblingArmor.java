package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class EntityCrumblingArmor extends BaseGeoEntity {
    private static final int EXECUTE_ANIMATION_TICKS = 110;
    private static final int EXECUTE_HIT_TICK = 64;

    private int executeTimer = 0;
    private LivingEntity executeTarget = null;

    public EntityCrumblingArmor(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "crumbling_armor";
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.8D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.executeTimer > 0 || !(target instanceof LivingEntity living)) {
            return true;
        }
        this.executeTimer = EXECUTE_ANIMATION_TICKS;
        this.executeTarget = living;
        this.setAnimation("execute");
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.executeTimer <= 0) return;

        this.executeTimer--;
        int elapsed = EXECUTE_ANIMATION_TICKS - this.executeTimer;
        if (elapsed == EXECUTE_HIT_TICK && canHitExecuteTarget()) {
            EntityUtil.clearHurtTime(this.executeTarget, () ->
                    this.executeTarget.hurt(DamageHelper.getDamage(this, "black"), 10.0F));
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        if (this.executeTimer == 0) {
            this.executeTarget = null;
            this.setAnimation("idle");
        }
    }

    private boolean canHitExecuteTarget() {
        return this.executeTarget != null
                && this.executeTarget.isAlive()
                && this.distanceToSqr(this.executeTarget) <= 9.0D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCrumblingArmor> event) {
        if ("execute".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.crumbling_armor.execute"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.crumbling_armor.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
