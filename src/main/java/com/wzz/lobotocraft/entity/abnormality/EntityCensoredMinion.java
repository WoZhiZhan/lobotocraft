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

/** CENSORED-1 spawned after CENSORED consumes a victim or tombstone. */
public class EntityCensoredMinion extends BaseGeoEntity {
    private static final int DAMAGE_INTERVAL_TICKS = 50;
    private static final double DAMAGE_RANGE = 2.0D;
    private static final double TARGET_SCAN_RANGE = 24.0D;
    private static final int TARGET_SCAN_INTERVAL = 10;

    private int damageCooldown;
    private int targetScanCooldown;
    private Player cachedTarget;
    private boolean playedSpawnSound;

    public EntityCensoredMinion(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
    }

    @Override
    public String name() {
        return "censored1";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!playedSpawnSound && tickCount >= 2) {
            playedSpawnSound = true;
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.CENSORED_MINION_SPAWN.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        if (damageCooldown > 0) damageCooldown--;

        Player target = findTarget();
        if (target == null) {
            cachedTarget = null;
            getNavigation().stop();
            return;
        }
        if (distanceToSqr(target) > DAMAGE_RANGE * DAMAGE_RANGE) {
            getNavigation().moveTo(target, 1.0D);
            return;
        }

        getNavigation().stop();
        getLookControl().setLookAt(target);
        if (damageCooldown <= 0) {
            damageCooldown = DAMAGE_INTERVAL_TICKS;
            float damage = 13 + random.nextInt(4);
            EntityUtil.clearHurtTime(target, () ->
                    target.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage));
        }
    }

    private Player findTarget() {
        if (cachedTarget != null
                && cachedTarget.isAlive()
                && !cachedTarget.isRemoved()
                && cachedTarget.level() == level()
                && distanceToSqr(cachedTarget) <= TARGET_SCAN_RANGE * TARGET_SCAN_RANGE) {
            if (targetScanCooldown > 0) {
                targetScanCooldown--;
                return cachedTarget;
            }
        } else {
            cachedTarget = null;
        }
        targetScanCooldown = TARGET_SCAN_INTERVAL;

        AABB box = getBoundingBox().inflate(TARGET_SCAN_RANGE);
        List<Player> players = level().getEntitiesOfClass(Player.class, box,
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

    private PlayState predicate(AnimationState<EntityCensoredMinion> event) {
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
