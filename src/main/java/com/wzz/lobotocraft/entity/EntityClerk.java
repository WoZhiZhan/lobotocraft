package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntityClerk extends PathfinderMob {
    public static final int TEXTURE_VARIANT_COUNT = 16;
    public static final String NO_TOMBSTONE_TAG = "lobotocraft_no_clerk_tombstone";

    private static final EntityDataAccessor<Integer> DATA_TEXTURE_VARIANT =
            SynchedEntityData.defineId(EntityClerk.class, EntityDataSerializers.INT);

    @Nullable
    private BlockPos reactorPos;

    public EntityClerk(EntityType<? extends EntityClerk> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TEXTURE_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.3D));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(
                this,
                AbstractAbnormality.class,
                entity -> entity instanceof AbstractAbnormality,
                10.0F,
                1.0D,
                1.25D,
                entity -> true
        ));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public @NotNull SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                 MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                                 @Nullable CompoundTag tag) {
        setTextureVariant(this.random.nextInt(TEXTURE_VARIANT_COUNT));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 100 == 0 && this.isAlive()
                && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
    }

    public int getTextureVariant() {
        return Mth.clamp(this.entityData.get(DATA_TEXTURE_VARIANT), 0, TEXTURE_VARIANT_COUNT - 1);
    }

    public void setTextureVariant(int variant) {
        this.entityData.set(DATA_TEXTURE_VARIANT, Mth.clamp(variant, 0, TEXTURE_VARIANT_COUNT - 1));
    }

    public void setReactorPos(@Nullable BlockPos reactorPos) {
        this.reactorPos = reactorPos == null ? null : reactorPos.immutable();
    }

    @Nullable
    public BlockPos getReactorPos() {
        return reactorPos;
    }

    public static void markNoTombstone(Mob mob) {
        mob.getPersistentData().putBoolean(NO_TOMBSTONE_TAG, true);
    }

    public static void markNoTombstone(net.minecraft.world.entity.LivingEntity entity) {
        entity.getPersistentData().putBoolean(NO_TOMBSTONE_TAG, true);
    }

    public boolean shouldCreateTombstone() {
        return !this.getPersistentData().getBoolean(NO_TOMBSTONE_TAG);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TextureVariant", getTextureVariant());
        if (reactorPos != null) {
            tag.putBoolean("HasReactor", true);
            tag.putInt("ReactorX", reactorPos.getX());
            tag.putInt("ReactorY", reactorPos.getY());
            tag.putInt("ReactorZ", reactorPos.getZ());
        } else {
            tag.putBoolean("HasReactor", false);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTextureVariant(tag.getInt("TextureVariant"));
        if (tag.getBoolean("HasReactor")) {
            reactorPos = new BlockPos(tag.getInt("ReactorX"), tag.getInt("ReactorY"), tag.getInt("ReactorZ"));
        } else {
            reactorPos = null;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }
}
