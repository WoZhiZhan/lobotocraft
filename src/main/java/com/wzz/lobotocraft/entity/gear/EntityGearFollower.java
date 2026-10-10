package com.wzz.lobotocraft.entity.gear;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

/** Wild church resident, not a containment abnormality or facility employee. */
public final class EntityGearFollower extends BaseGeoEntity {
    private long churchAnchor;
    private int churchSlot = -1;
    private int attackTicks;
    public EntityGearFollower(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }
    @Override public String name() { return "gear_follower"; }
    public void setChurch(long anchor, int slot) { churchAnchor = anchor; churchSlot = slot; }
    public int churchSlot() { return churchSlot; }
    public long churchAnchor() { return churchAnchor; }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 86)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.3)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new HurtByTargetGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                target -> target.isAlive() && !target.isSpectator() && !((Player) target).isCreative()));
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (attackTicks > 0 || !(target instanceof LivingEntity living)) return false;
        attackTicks = 20;
        setAnimation(random.nextBoolean() ? "attack1" : "attack2");
        return living.hurt(DamageHelper.getDamage(this, "red"), 6 + random.nextInt(3));
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && attackTicks > 0 && --attackTicks == 0) setAnimation("idle");
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, state -> {
            String animation = getAnimation();
            if ("idle".equals(animation))
                return state.setAndContinue(RawAnimation.begin().thenLoop(state.isMoving() ? "walk" : "idle"));
            return state.setAndContinue(RawAnimation.begin().thenPlay(animation));
        }));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("ChurchAnchor", churchAnchor); tag.putInt("ChurchSlot", churchSlot);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        churchAnchor = tag.getLong("ChurchAnchor"); churchSlot = tag.contains("ChurchSlot") ? tag.getInt("ChurchSlot") : -1;
    }
}
