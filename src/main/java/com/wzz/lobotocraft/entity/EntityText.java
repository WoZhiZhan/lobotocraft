package com.wzz.lobotocraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class EntityText extends Entity {
    private static final EntityDataAccessor<String> TEXT =
            SynchedEntityData.defineId(EntityText.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MAX_LIFE =
            SynchedEntityData.defineId(EntityText.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(EntityText.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROTATION =
            SynchedEntityData.defineId(EntityText.class, EntityDataSerializers.FLOAT);

    public EntityText(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public void setText(String text) {
        entityData.set(TEXT, text);
    }

    public String getText() {
        return entityData.get(TEXT);
    }

    public void setLifeTick(int lifeTick) {
        entityData.set(MAX_LIFE, lifeTick);
    }

    public int getMaxLifeTick() {
        return entityData.get(MAX_LIFE);
    }

    public void setColor(int color) {
        entityData.set(COLOR, color);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    public void setRotation(float degrees) { entityData.set(ROTATION, degrees); }

    public float getRotation() { return entityData.get(ROTATION); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount >= getMaxLifeTick()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TEXT, "");
        this.entityData.define(MAX_LIFE, 60);
        this.entityData.define(COLOR, 0xFFFFFF);
        this.entityData.define(ROTATION, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setText(tag.getString("Text"));
        this.setLifeTick(tag.getInt("LifeTick"));
        if (tag.contains("Color")) this.setColor(tag.getInt("Color"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Text", this.getText());
        tag.putInt("LifeTick", this.getMaxLifeTick());
        tag.putInt("Color", this.getColor());
    }
}