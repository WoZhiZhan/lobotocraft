package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;

public final class GeburahDamageResponsibility {
    private GeburahDamageResponsibility() {
    }

    public static LivingEntity resolve(DamageSource source) {
        if (source == null) return null;
        LivingEntity responsible = ownerOf(source.getEntity());
        if (responsible != null) return responsible;
        return ownerOf(source.getDirectEntity());
    }

    private static LivingEntity ownerOf(Entity entity) {
        if (entity instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            return ownerOf(owner);
        }
        if (entity instanceof TamableAnimal tamable) {
            LivingEntity owner = tamable.getOwner();
            if (owner != null) return ownerOf(owner);
        }
        return entity instanceof LivingEntity living ? living : null;
    }
}
