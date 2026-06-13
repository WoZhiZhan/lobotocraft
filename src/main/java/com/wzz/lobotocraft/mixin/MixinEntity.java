package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow public abstract double getX();

    @Shadow public abstract double getY();

    @Shadow public abstract double getZ();

    @Shadow public Level level;

    @Unique
    private final Entity lobotocraft$entity = (Entity) (Object) this;

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void setPos(double p_20210_, double p_20211_, double p_20212_, CallbackInfo ci) {
        if (lobotocraft$entity instanceof ServerPlayer serverPlayer && (WorkManager.isPlayerWorking(serverPlayer)
                || serverPlayer.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void setPosRaw(double p_20210_, double p_20211_, double p_20212_, CallbackInfo ci) {
        if (lobotocraft$entity instanceof ServerPlayer serverPlayer && (WorkManager.isPlayerWorking(serverPlayer)
                || serverPlayer.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }

    @Inject(method = "setBoundingBox", at = @At("HEAD"), cancellable = true)
    private void setBoundingBox(AABB p_20012_, CallbackInfo ci) {
        if (lobotocraft$entity instanceof ServerPlayer serverPlayer && (WorkManager.isPlayerWorking(serverPlayer)
                || serverPlayer.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void teleportTo(double p_20210_, double p_20211_, double p_20212_, CallbackInfo ci) {
        if (lobotocraft$entity instanceof ServerPlayer serverPlayer && (WorkManager.isPlayerWorking(serverPlayer)
                || serverPlayer.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }

    @Inject(method = "moveTo(DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void moveTo(double p_20108_, double p_20109_, double p_20110_, float p_20111_, float p_20112_, CallbackInfo ci) {
        if (lobotocraft$entity instanceof ServerPlayer serverPlayer && (WorkManager.isPlayerWorking(serverPlayer)
                || serverPlayer.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }
}