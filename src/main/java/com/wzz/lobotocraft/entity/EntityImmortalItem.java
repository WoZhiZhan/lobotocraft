package com.wzz.lobotocraft.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class EntityImmortalItem extends ItemEntity {
    public EntityImmortalItem(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        this.setPickUpDelay(5);
        this.lifespan = 3600;
        this.setUnlimitedLifetime();
    }

    public static EntityImmortalItem create(EntityType<EntityImmortalItem> type, Level level, double x, double y, double z, ItemStack itemStack) {
        EntityImmortalItem entity = type.create(level);
        if (entity != null) {
            entity.setPos(x, y, z);
            entity.setItem(itemStack);
        }

        return entity;
    }

    public boolean hurt(@NotNull DamageSource source, float amount) {
        return source == this.damageSources().fellOutOfWorld();
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            Player targetPlayer = this.level().getNearestPlayer(this, 5);
            if (targetPlayer != null) {
                Vec3 direction = (new Vec3(targetPlayer.getX() - this.getX(), targetPlayer.getY() + (double)targetPlayer.getEyeHeight() - this.getY(), targetPlayer.getZ() - this.getZ())).normalize();
                this.setDeltaMovement(direction.scale(5));
                this.setDeltaMovement(this.getDeltaMovement().add(0.0F, -0.02, 0.0F));
            }
        }

    }

    public void remove(@NotNull Entity.@NotNull RemovalReason pReason) {
        super.remove(pReason);
    }

    public boolean fireImmune() {
        return true;
    }

    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
    }
}
