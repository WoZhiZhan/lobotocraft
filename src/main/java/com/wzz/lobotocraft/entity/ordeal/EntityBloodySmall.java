package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.listener.CrimsonDawnEvent;
import com.wzz.lobotocraft.event.listener.CrimsonNoonEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 血色的黎明 - "开始欢呼吧！"
 */
public class EntityBloodySmall extends BaseGeoEntity {
    private static final int TELEPORT_INTERVAL = 20 * 20;
    private static final int NOTICE_COOLDOWN_TICKS = 10 * 20;
    private static final double ABNORMALITY_NOTICE_RANGE_SQR = 8.0D * 8.0D;

    private UUID trackedAbnormality;
    private int teleportTimer = 0;
    private int noticeCooldown = 0;
    private boolean countedDeath = false;
    private boolean crimsonNoonSpawn = false;

    public EntityBloodySmall(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "bloody_small";
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    public void setTrackedAbnormality(AbstractAbnormality abnormality) {
        if (abnormality == null) return;
        this.trackedAbnormality = abnormality.getUUID();
        tryNotifyAbnormalityLocation(abnormality);
    }

    public boolean isCrimsonNoonSpawn() {
        return crimsonNoonSpawn;
    }

    public void setCrimsonNoonSpawn(boolean crimsonNoonSpawn) {
        this.crimsonNoonSpawn = crimsonNoonSpawn;
        this.getPersistentData().putBoolean(CrimsonNoonEvent.CRIMSON_NOON_CLOWN_TAG, crimsonNoonSpawn);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel level)) return;

        if (!crimsonNoonSpawn && this.getPersistentData().getBoolean(CrimsonNoonEvent.CRIMSON_NOON_CLOWN_TAG)) {
            crimsonNoonSpawn = true;
        }

        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        if (noticeCooldown > 0) noticeCooldown--;
        notifyIfNearTrackedAbnormality(level);

        teleportTimer++;
        if (teleportTimer >= TELEPORT_INTERVAL) {
            teleportTimer = 0;
            burstAndMove(level);
        }
    }

    private void notifyIfNearTrackedAbnormality(ServerLevel level) {
        if (noticeCooldown > 0 || trackedAbnormality == null) return;
        Entity entity = level.getEntity(trackedAbnormality);
        if (entity instanceof AbstractAbnormality abnormality
                && abnormality.isAlive()
                && !abnormality.hasEscape()
                && this.distanceToSqr(abnormality) <= ABNORMALITY_NOTICE_RANGE_SQR) {
            tryNotifyAbnormalityLocation(abnormality);
        }
    }

    private void tryNotifyAbnormalityLocation(AbstractAbnormality abnormality) {
        if (noticeCooldown > 0) return;
        noticeCooldown = NOTICE_COOLDOWN_TICKS;
        CrimsonDawnEvent.notifyAbnormalityLocation(this, abnormality);
    }

    private void burstAndMove(ServerLevel level) {
        setAnimation("skill");
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                8, 0.8D, 0.6D, 0.8D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5D, getZ(),
                40, 1.0D, 0.8D, 1.0D, 0.02D);

        AbstractAbnormality current = getTrackedAbnormality(level);
        if (current != null && !current.hasEscape()) {
            if (current instanceof EntityDarkSkadi) {
                notifySkadiCounterProtected(level);
            } else if (current.getQliphothCounter() > 0) {
                current.decreaseQliphothCounter(1);
            }
        }
        decreaseTodayWorkCount(level);

        AbstractAbnormality next = chooseNextAbnormality(level, current);
        if (next != null) {
            BlockPos pos = CrimsonDawnEvent.findBloodySmallSpawnPosition(level, this, next.blockPosition(), 4);
            if (pos != null) {
                this.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                setTrackedAbnormality(next);
            }
        }
        setAnimation("idle");
    }

    private void notifySkadiCounterProtected(ServerLevel level) {
        Component message = Component.literal("被祝福的玩家没有死亡，斯卡蒂感到安心，计数器并未降低")
                .withStyle(ChatFormatting.BLUE);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    private void decreaseTodayWorkCount(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                data.setTodayWorkCount(Math.max(0, data.getTodayWorkCount() - 1));
                MessageLoader.getLoader().sendToPlayer(player,
                        new CompanyDailySyncPacket(
                                data.getCurrentDay(),
                                data.getTodayWorkCount(),
                                data.isArmorLocked(),
                                data.isHasSleep()
                        ));
            });
        }
    }

    @Nullable
    private AbstractAbnormality getTrackedAbnormality(ServerLevel level) {
        if (trackedAbnormality == null) return null;
        Entity entity = level.getEntity(trackedAbnormality);
        return entity instanceof AbstractAbnormality abnormality
                && abnormality.isAlive()
                && !abnormality.hasEscape()
                ? abnormality
                : null;
    }

    @Nullable
    private AbstractAbnormality chooseNextAbnormality(ServerLevel level, @Nullable AbstractAbnormality current) {
        List<AbstractAbnormality> candidates = new ArrayList<>(CrimsonDawnEvent.findCandidateAbnormalities(level));
        if (current != null && candidates.size() > 1) {
            candidates.removeIf(abnormality -> abnormality.getUUID().equals(current.getUUID()));
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !countedDeath && this.level() instanceof ServerLevel level) {
            countedDeath = true;
            if (crimsonNoonSpawn || this.getPersistentData().getBoolean(CrimsonNoonEvent.CRIMSON_NOON_CLOWN_TAG)) {
                CrimsonNoonEvent.onCrimsonNoonClownKilled(level);
            } else {
                CrimsonDawnEvent.onBloodySmallKilled(level);
            }
        }
        super.die(source);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityBloodySmall> event) {
        String animation = getAnimation();
        if ("skill".equals(animation)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("skill"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.3D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.3D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (trackedAbnormality != null) tag.putUUID("TrackedAbnormality", trackedAbnormality);
        tag.putInt("TeleportTimer", teleportTimer);
        tag.putInt("NoticeCooldown", noticeCooldown);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("CrimsonNoonSpawn", crimsonNoonSpawn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("TrackedAbnormality")) trackedAbnormality = tag.getUUID("TrackedAbnormality");
        teleportTimer = tag.getInt("TeleportTimer");
        noticeCooldown = tag.getInt("NoticeCooldown");
        countedDeath = tag.getBoolean("CountedDeath");
        crimsonNoonSpawn = tag.getBoolean("CrimsonNoonSpawn")
                || this.getPersistentData().getBoolean(CrimsonNoonEvent.CRIMSON_NOON_CLOWN_TAG);
        if (crimsonNoonSpawn) {
            this.getPersistentData().putBoolean(CrimsonNoonEvent.CRIMSON_NOON_CLOWN_TAG, true);
        }
    }
}
