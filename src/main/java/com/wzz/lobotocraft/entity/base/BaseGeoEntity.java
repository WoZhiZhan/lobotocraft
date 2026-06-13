package com.wzz.lobotocraft.entity.base;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public abstract class BaseGeoEntity extends TamableAnimal implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final EntityDataAccessor<String> TEXTURE =
            SynchedEntityData.defineId(BaseGeoEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> DATA_ANIMATION =
            SynchedEntityData.defineId(BaseGeoEntity.class, EntityDataSerializers.STRING);

    public BaseGeoEntity(EntityType<? extends TamableAnimal> p_21368_, Level p_21369_) {
        super(p_21368_, p_21369_);
    }

    /** 异想体/自定义实体不可繁殖 */
    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
    }

    public abstract String name();

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TEXTURE, name());
        // 修复:此前漏注册 DATA_ANIMATION,导致调用 setAnimation 时 DataItem 为 null 而崩溃
        this.entityData.define(DATA_ANIMATION, "idle");
    }

    public void setAnimation(String name) {
        this.entityData.set(DATA_ANIMATION, name);
    }

    public String getAnimation() {
        return this.entityData.get(DATA_ANIMATION);
    }

    public void setTexture(String texture) {
        this.entityData.set(TEXTURE, texture);
    }

    public String getTexture() {
        return this.entityData.get(TEXTURE);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Texture")) {
            this.setTexture(compound.getString("Texture"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("Texture", this.getTexture());
    }
}