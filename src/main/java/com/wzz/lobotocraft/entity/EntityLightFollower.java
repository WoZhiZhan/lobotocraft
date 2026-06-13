package com.wzz.lobotocraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.UUID;

public class EntityLightFollower extends Entity {
    private UUID ownerUUID;
    private int lightLevel;
    
    public EntityLightFollower(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvisible(true);
    }

    public void setLightLevel(int lightLevel) {
        this.lightLevel = lightLevel;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    @Override
    protected void defineSynchedData() {

    }

    @Override
    public void tick() {
        super.tick();
        if (ownerUUID != null && !level().isClientSide) {
            Entity owner = ((ServerLevel)level()).getEntity(ownerUUID);
            if (owner instanceof LivingEntity living && owner.isAlive()) {
                this.setPos(living.getX(), living.getY() + 1, living.getZ());
                BlockPos pos = this.blockPosition();
                if (level().getBlockState(pos).isAir()) {
                    level().setBlock(pos, 
                        Blocks.LIGHT.defaultBlockState()
                            .setValue(LightBlock.LEVEL, lightLevel),
                        3);
                }
            } else {
                this.discard();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        this.ownerUUID = compoundTag.getUUID("OwnerUUID");
        this.lightLevel = compoundTag.getInt("LightLevel");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        compoundTag.putUUID("OwnerUUID", this.ownerUUID);
        compoundTag.putInt("LightLevel", this.lightLevel);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        BlockPos pos = this.blockPosition();
        if (level().getBlockState(pos).is(Blocks.LIGHT)) {
            level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        super.remove(reason);
    }
}