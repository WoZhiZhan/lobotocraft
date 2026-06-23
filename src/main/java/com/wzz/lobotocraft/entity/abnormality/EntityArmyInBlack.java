package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityArmyInBlack extends AbstractAbnormality {
    public static final String PROTECTION_UNTIL_TAG = "lobotocraft_army_in_black_protection_until";
    private static final int PROTECTION_DURATION_TICKS = 20 * 120;
    private static final int PROTECTION_HEAL_INTERVAL = 20 * 10;
    private static final double PROTECTION_FOLLOW_DISTANCE_SQR = 25.0D;
    private static final double PROTECTION_TELEPORT_DISTANCE_SQR = 100.0D;
    private static final double PROTECTION_HEAL_RADIUS = 10.0D;
    private static final float PROTECTION_HEAL_AMOUNT = 12.0F;

    private UUID protectedPlayerId = null;
    private BlockPos protectionReturnPos = null;
    private long protectionEndsAt = 0L;
    private int protectionHealTimer = 0;

    public EntityArmyInBlack(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "D-01-106";
        this.abnormalityName = "深黯“军团”";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 30;

        float[] basePreferences = {0.45f, 0.45f, 1.0f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0, true, true, true)
        };
    }

    @Override
    public int getBasicInfoCost() { return 30; }

    @Override
    public int getSensitiveInfoCost() { return 30; }

    @Override
    public int getManualCost(int manualIndex) { return 6; }

    @Override
    public int getWorkPreferencesCost() { return 10; }

    @Override
    public String name() {
        return "army_in_black";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.9D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                target -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!hasEscape()) {
            if (tickProtection()) {
                return;
            }
            setNoAi(true);
            this.noPhysics = false;
            setTarget(null);
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }

        setNoAi(false);
        this.noPhysics = false;
    }

    @Override
    public boolean shouldGivePEBox(ServerPlayer player, WorkType workType, WorkResult result, int peOutput) {
        return workType != WorkType.ATTACHMENT;
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (workType == WorkType.ATTACHMENT) {
            protectEmployee(player);
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§d深黯“军团”正在守护你。"), true);
        }
        if (workType == WorkType.REPRESSION) {
            decreaseQliphothCounter(1);
        }
    }

    private void protectEmployee(ServerPlayer player) {
        stopProtection();
        this.protectedPlayerId = player.getUUID();
        this.protectionReturnPos = this.blockPosition();
        this.protectionEndsAt = player.level().getGameTime() + PROTECTION_DURATION_TICKS;
        this.protectionHealTimer = 0;
        refreshProtectionTag(player);
    }

    private boolean tickProtection() {
        if (this.protectedPlayerId == null) {
            return false;
        }
        ServerPlayer player = ((ServerLevel) this.level()).getServer().getPlayerList().getPlayer(this.protectedPlayerId);
        if (player == null
                || player.level() != this.level()
                || !player.isAlive()
                || MentalValueUtil.getMentalValue(player) <= 0.0F
                || this.level().getGameTime() >= this.protectionEndsAt) {
            stopProtection();
            return false;
        }

        refreshProtectionTag(player);
        setNoAi(false);
        this.noPhysics = true;
        setTarget(null);

        followProtectedPlayer(player);

        this.protectionHealTimer++;
        if (this.protectionHealTimer >= PROTECTION_HEAL_INTERVAL) {
            healProtectedArea(player);
            this.protectionHealTimer = 0;
        }
        return true;
    }

    private void followProtectedPlayer(ServerPlayer player) {
        double distanceSqr = this.distanceToSqr(player);
        if (distanceSqr > PROTECTION_TELEPORT_DISTANCE_SQR) {
            Vec3 followPos = player.position().subtract(player.getLookAngle().scale(1.5D));
            this.moveTo(followPos.x, player.getY(), followPos.z, this.getYRot(), this.getXRot());
            this.getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (distanceSqr > PROTECTION_FOLLOW_DISTANCE_SQR) {
            this.getNavigation().moveTo(player, 0.9D);
            return;
        }

        this.getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    private void healProtectedArea(ServerPlayer protectedPlayer) {
        for (ServerPlayer nearby : protectedPlayer.level().getEntitiesOfClass(ServerPlayer.class,
                protectedPlayer.getBoundingBox().inflate(PROTECTION_HEAL_RADIUS), ServerPlayer::isAlive)) {
            nearby.heal(PROTECTION_HEAL_AMOUNT);
            MentalValueUtil.addMentalValue(nearby, PROTECTION_HEAL_AMOUNT);
            if (nearby.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        nearby.getX(), nearby.getY() + 1.0D, nearby.getZ(),
                        5, 0.35D, 0.35D, 0.35D, 0.02D);
            }
        }
    }

    private void refreshProtectionTag(ServerPlayer player) {
        player.getPersistentData().putLong(PROTECTION_UNTIL_TAG, this.protectionEndsAt);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
    }

    private void stopProtection() {
        BlockPos returnPos = this.protectionReturnPos;
        if (this.protectedPlayerId != null && this.level() instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.protectedPlayerId);
            if (player != null && player.getPersistentData().getLong(PROTECTION_UNTIL_TAG) <= this.protectionEndsAt) {
                player.getPersistentData().remove(PROTECTION_UNTIL_TAG);
                player.removeEffect(MobEffects.GLOWING);
            }
        }
        this.protectedPlayerId = null;
        this.protectionReturnPos = null;
        this.protectionEndsAt = 0L;
        this.protectionHealTimer = 0;
        this.noPhysics = false;
        this.getNavigation().stop();
        if (returnPos != null) {
            this.moveTo(returnPos.getX() + 0.5D, returnPos.getY(), returnPos.getZ() + 0.5D,
                    this.getYRot(), this.getXRot());
            setDeltaMovement(Vec3.ZERO);
        }
    }

    public static boolean hasActiveProtection(ServerPlayer player) {
        long until = player.getPersistentData().getLong(PROTECTION_UNTIL_TAG);
        if (until <= player.level().getGameTime()
                || !player.isAlive()
                || MentalValueUtil.getMentalValue(player) <= 0.0F) {
            player.getPersistentData().remove(PROTECTION_UNTIL_TAG);
            return false;
        }
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.protectedPlayerId == null && super.canBeCollidedWith();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 6.0F + this.random.nextInt(5);
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:white"), damage);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        EntityUtil.clearHurtTime(living, () ->
                living.hurt(DamageHelper.getDamage(this, this.random.nextBoolean()
                        ? "lobotocraft:black" : "lobotocraft:white"), 22.0F + this.random.nextInt(7)));
        return true;
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“深黯“军团””正在向员工讲述有关“爱”的一切。");
        logs.add("“深黯“军团””致力于守护拥有善良之心的人。");
        logs.add("“深黯“军团””的粉红迷彩让员工感受到了希望。");
        return logs;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityArmyInBlack> event) {
        if (isMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.army_in_black.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.army_in_black.idle"));
    }

    private boolean isMovingForAnimation(AnimationState<EntityArmyInBlack> event) {
        Vec3 movement = this.getDeltaMovement();
        return hasEscape() && (event.isMoving()
                || movement.x * movement.x + movement.z * movement.z > 1.0E-6D
                || !this.getNavigation().isDone());
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityArmyInBlack((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.protectedPlayerId != null) {
            tag.putUUID("ProtectedPlayer", this.protectedPlayerId);
        }
        if (this.protectionReturnPos != null) {
            tag.putLong("ProtectionReturnPos", this.protectionReturnPos.asLong());
        }
        tag.putLong("ProtectionEndsAt", this.protectionEndsAt);
        tag.putInt("ProtectionHealTimer", this.protectionHealTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ProtectedPlayer")) {
            this.protectedPlayerId = tag.getUUID("ProtectedPlayer");
        }
        if (tag.contains("ProtectionReturnPos")) {
            this.protectionReturnPos = BlockPos.of(tag.getLong("ProtectionReturnPos"));
        }
        this.protectionEndsAt = tag.getLong("ProtectionEndsAt");
        this.protectionHealTimer = tag.getInt("ProtectionHealTimer");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 450.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 22.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.2D);
    }
}
