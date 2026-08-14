package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;
import java.util.List;

public class EntityCENSORED1 extends BaseGeoEntity {
    private static final int DAMAGE_INTERVAL_TICKS = 50;
    private static final double DAMAGE_RANGE = 2.0D;
    private static final double TARGET_SCAN_RANGE = 24.0D;
    private static final int TARGET_SCAN_INTERVAL = 10;

    private int damageCooldown = 0;
    private int targetScanCooldown = 0;
    private Player cachedTarget = null;
    private boolean playedSpawnSound = false;

    public EntityCENSORED1(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "censored1";
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!playedSpawnSound && this.tickCount >= 2) {
            playedSpawnSound = true;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.CENSORED1_SPAWN.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        }

        if (damageCooldown > 0) {
            damageCooldown--;
        }

        Player target = findTarget();
        if (target == null) {
            cachedTarget = null;
            this.getNavigation().stop();
            return;
        }
        double distSq = this.distanceToSqr(target);
        if (distSq > DAMAGE_RANGE * DAMAGE_RANGE) {
            this.getNavigation().moveTo(target, 1.0D);
            return;
        }

        this.getNavigation().stop();
        this.getLookControl().setLookAt(target);
        if (damageCooldown <= 0) {
            damageCooldown = DAMAGE_INTERVAL_TICKS;
            float damage = 13 + this.random.nextInt(4);
            EntityUtil.clearHurtTime(target, () ->
                    target.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage));
        }
    }

    private Player findTarget() {
        if (cachedTarget != null
                && cachedTarget.isAlive()
                && !cachedTarget.isRemoved()
                && cachedTarget.level() == this.level()
                && this.distanceToSqr(cachedTarget) <= TARGET_SCAN_RANGE * TARGET_SCAN_RANGE) {
            if (targetScanCooldown > 0) {
                targetScanCooldown--;
                return cachedTarget;
            }
        } else {
            cachedTarget = null;
        }
        targetScanCooldown = TARGET_SCAN_INTERVAL;

        AABB box = this.getBoundingBox().inflate(TARGET_SCAN_RANGE);
        List<Player> players = this.level().getEntitiesOfClass(Player.class, box,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator());
        cachedTarget = players.stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        return cachedTarget;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCENSORED1> event) {
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, TARGET_SCAN_RANGE);
    }
}
